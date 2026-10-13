package com.linenexus.asset.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.linenexus.asset.audit.AuditActions;
import com.linenexus.asset.audit.AuditService;
import com.linenexus.asset.audit.ClientIps;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final AuditService auditService;

    public AuthController(AuthService authService, AuditService auditService) {
        this.authService = authService;
        this.auditService = auditService;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest req, HttpServletRequest http) {
        String ip = ClientIps.of(http);
        String tenant = req == null || req.tenantId() == null || req.tenantId().isBlank()
                ? "tenant-dev"
                : req.tenantId().trim();
        String username = req == null || req.username() == null ? "" : req.username().trim();
        try {
            LoginResponse res = authService.login(req);
            auditService.record(
                    res.user().tenantId(),
                    AuditActions.CAT_AUTH,
                    AuditActions.LOGIN,
                    AuditActions.SUCCESS,
                    res.user().id(),
                    res.user().username(),
                    null,
                    "用户登录成功",
                    null,
                    ip);
            return res;
        } catch (ResponseStatusException e) {
            auditService.record(
                    tenant,
                    AuditActions.CAT_AUTH,
                    AuditActions.LOGIN_FAIL,
                    AuditActions.FAILURE,
                    null,
                    username.isEmpty() ? null : username,
                    null,
                    "登录失败：" + safeMsg(e),
                    null,
                    ip);
            throw e;
        }
    }

    @PostMapping("/platform/login")
    public LoginResponse platformLogin(@RequestBody LoginRequest req, HttpServletRequest http) {
        String ip = ClientIps.of(http);
        String username = req == null || req.username() == null ? "" : req.username().trim();
        try {
            LoginResponse res = authService.platformLogin(req);
            auditService.record(
                    PlatformIds.PLATFORM_TENANT_ID,
                    AuditActions.CAT_AUTH,
                    AuditActions.LOGIN,
                    AuditActions.SUCCESS,
                    res.user().id(),
                    res.user().username(),
                    null,
                    "平台运营登录成功",
                    null,
                    ip);
            return res;
        } catch (ResponseStatusException e) {
            auditService.record(
                    PlatformIds.PLATFORM_TENANT_ID,
                    AuditActions.CAT_AUTH,
                    AuditActions.LOGIN_FAIL,
                    AuditActions.FAILURE,
                    null,
                    username.isEmpty() ? null : username,
                    null,
                    "平台登录失败：" + safeMsg(e),
                    null,
                    ip);
            throw e;
        }
    }

    @GetMapping("/me")
    public AuthUser me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return authService.me(authorization);
    }

    @PutMapping("/me")
    public AuthUser updateMe(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody UpdateProfileRequest req,
            HttpServletRequest http) {
        String ip = ClientIps.of(http);
        try {
            AuthUser user = authService.updateProfile(authorization, req);
            auditService.record(
                    user.tenantId(),
                    AuditActions.CAT_AUTH,
                    AuditActions.PROFILE_UPDATE,
                    AuditActions.SUCCESS,
                    user.id(),
                    user.username(),
                    null,
                    "更新个人资料：" + user.displayName(),
                    null,
                    ip);
            return user;
        } catch (ResponseStatusException e) {
            AuthUser me = safeMe(authorization);
            auditService.record(
                    me == null ? "tenant-dev" : me.tenantId(),
                    AuditActions.CAT_AUTH,
                    AuditActions.PROFILE_UPDATE,
                    AuditActions.FAILURE,
                    me == null ? null : me.id(),
                    me == null ? null : me.username(),
                    null,
                    "更新个人资料失败：" + safeMsg(e),
                    null,
                    ip);
            throw e;
        }
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ChangePasswordRequest req,
            HttpServletRequest http) {
        String ip = ClientIps.of(http);
        AuthUser me = safeMe(authorization);
        try {
            authService.changePassword(authorization, req);
            if (me != null) {
                auditService.record(
                        me.tenantId(),
                        AuditActions.CAT_AUTH,
                        AuditActions.PASSWORD_CHANGE,
                        AuditActions.SUCCESS,
                        me.id(),
                        me.username(),
                        null,
                        "修改登录密码",
                        null,
                        ip);
            }
        } catch (ResponseStatusException e) {
            auditService.record(
                    me == null ? "tenant-dev" : me.tenantId(),
                    AuditActions.CAT_AUTH,
                    AuditActions.PASSWORD_CHANGE,
                    AuditActions.FAILURE,
                    me == null ? null : me.id(),
                    me == null ? null : me.username(),
                    null,
                    "修改密码失败：" + safeMsg(e),
                    null,
                    ip);
            throw e;
        }
    }

    @GetMapping("/roles")
    @ResponseStatus(HttpStatus.OK)
    public Object rolesHint() {
        return java.util.Map.of(
                "admin", "全部菜单与写操作",
                "operator", "执行/组态写值；不可改网关与点表注册",
                "viewer", "看板与只读；不可下发/写点/改主数据",
                "platform_admin", "平台运营面：租户开通/启停（入口 /platform/login）",
                "demoAccounts",
                java.util.List.of(
                        java.util.Map.of("username", "admin", "password", "admin123", "roles", "admin"),
                        java.util.Map.of("username", "operator", "password", "op123", "roles", "operator"),
                        java.util.Map.of("username", "viewer", "password", "view123", "roles", "viewer"),
                        java.util.Map.of(
                                "username",
                                "platform",
                                "password",
                                "platform123",
                                "roles",
                                "platform_admin",
                                "loginPath",
                                "/platform/login")));
    }

    private AuthUser safeMe(String authorization) {
        try {
            return authService.me(authorization);
        } catch (Exception e) {
            return null;
        }
    }

    private static String safeMsg(ResponseStatusException e) {
        String r = e.getReason();
        return r == null || r.isBlank() ? e.getStatusCode().toString() : r;
    }
}
