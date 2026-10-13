package com.linenexus.gateway;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * JWT 校验：注入租户/用户/角色头；viewer 禁止写；operator 禁止改网关/点表注册。
 */
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    public static final String HDR_USER = "X-User-Id";
    public static final String HDR_USER_NAME = "X-User-Name";
    public static final String HDR_ROLES = "X-Roles";
    public static final String HDR_TENANT = "X-Tenant-Id";

    private final SecretKey key;

    public AuthGlobalFilter(
            @Value("${linenexus.auth.jwt-secret:linenexus-dev-jwt-secret-change-me-32b}") String jwtSecret) {
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();

        if (isPublic(path, method)) {
            return chain.filter(exchange);
        }

        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (auth == null || !auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return unauthorized(exchange, "未登录或缺少 Bearer token");
        }
        String token = auth.substring(7).trim();
        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (Exception e) {
            return unauthorized(exchange, "登录已失效，请重新登录");
        }

        String uid = str(claims.get("uid"));
        String username = claims.getSubject();
        String tenantId = str(claims.get("tenantId"));
        String roles = str(claims.get("roles"));
        List<String> roleList = splitRoles(roles);

        if (tenantId.isBlank()) {
            tenantId = "tenant-dev";
        }

        String deny = authorize(path, method, roleList);
        if (deny != null) {
            return forbidden(exchange, deny);
        }

        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(HDR_TENANT, tenantId)
                .header(HDR_USER, uid)
                .header(HDR_USER_NAME, username == null ? "" : username)
                .header(HDR_ROLES, roles)
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    private static boolean isPublic(String path, HttpMethod method) {
        if (method == HttpMethod.OPTIONS) {
            return true;
        }
        if (path.equals("/api/auth/login")
                || path.equals("/api/auth/platform/login")
                || path.equals("/api/auth/roles")) {
            return true;
        }
        return false;
    }

    /**
     * @return 拒绝原因；null 表示放行
     */
    private static String authorize(String path, HttpMethod method, List<String> roles) {
        boolean isGet = method == HttpMethod.GET || method == HttpMethod.HEAD;
        boolean platformAdmin = roles.contains("platform_admin");
        boolean admin = roles.contains("admin");
        boolean operator = roles.contains("operator");
        boolean viewer = roles.contains("viewer");

        // 平台运营面与租户业务面互斥
        if (path.startsWith("/api/platform/")) {
            return platformAdmin ? null : "仅平台运营账号可访问管理面";
        }
        if (platformAdmin) {
            return "平台运营账号不可访问租户业务接口";
        }

        if (admin) {
            return null;
        }

        // 个人资料 / 改密码：任意已登录租户用户可写
        if (isSelfProfileWrite(path, method)) {
            return null;
        }

        if (viewer && !operator && !admin) {
            if (!isGet) {
                return "观察员仅可只读访问";
            }
            return null;
        }

        if (operator) {
            if (isGet) {
                return null;
            }
            // 允许执行层写、HMI、边端读写、告警确认/规则；禁止网关/设备/点注册变更
            if (path.startsWith("/api/execution/")) {
                return null;
            }
            if (path.startsWith("/api/assets/hmi/")) {
                return null;
            }
            if (path.startsWith("/api/monitor/")) {
                return null;
            }
            if (isEdgeTelemetryWrite(path)) {
                return null;
            }
            if (path.startsWith("/api/assets/edge-gateways")) {
                return "操作员不可修改网关或点表注册，请使用管理员账号";
            }
            return null;
        }

        return "无有效角色，拒绝访问";
    }

    private static boolean isSelfProfileWrite(String path, HttpMethod method) {
        if (method == HttpMethod.PUT && path.equals("/api/auth/me")) {
            return true;
        }
        return method == HttpMethod.POST && path.equals("/api/auth/password");
    }

    /** 边端 browse / read / write 等运行态操作 */
    private static boolean isEdgeTelemetryWrite(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        return p.contains("/read")
                || p.contains("/write")
                || p.contains("/browse")
                || p.contains("/nodes")
                || p.endsWith("/points/read");
    }

    private static List<String> splitRoles(String roles) {
        if (roles == null || roles.isBlank()) {
            return List.of();
        }
        return Arrays.stream(roles.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        return writeJson(exchange, HttpStatus.UNAUTHORIZED, message);
    }

    private static Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        return writeJson(exchange, HttpStatus.FORBIDDEN, message);
    }

    private static Mono<Void> writeJson(ServerWebExchange exchange, HttpStatus status, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = ("{\"error\":\"" + status.getReasonPhrase() + "\",\"message\":\"" + escape(message) + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
