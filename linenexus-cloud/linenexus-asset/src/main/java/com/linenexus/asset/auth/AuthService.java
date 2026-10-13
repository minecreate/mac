package com.linenexus.asset.auth;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.linenexus.asset.tenant.TenantRepository;

import io.jsonwebtoken.Claims;

@Service
public class AuthService {

    private final IamUserRepository repository;
    private final TenantRepository tenantRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(
            IamUserRepository repository, TenantRepository tenantRepository, JwtService jwtService) {
        this.repository = repository;
        this.tenantRepository = tenantRepository;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest req) {
        if (req == null || req.username() == null || req.username().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username 不能为空");
        }
        if (req.password() == null || req.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password 不能为空");
        }
        String tenant = req.tenantId() == null || req.tenantId().isBlank() ? "tenant-dev" : req.tenantId().trim();
        if (PlatformIds.isPlatformTenant(tenant)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请从平台运营入口登录");
        }
        tenantRepository
                .findById(tenant)
                .ifPresent(t -> {
                    if (!"active".equalsIgnoreCase(t.getStatus())) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "租户已停用");
                    }
                });
        IamUserEntity user = repository
                .findByTenantIdAndUsername(tenant, req.username().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户已禁用");
        }
        if (PlatformIds.isPlatformAdmin(JwtService.splitRoles(user.getRoles()))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请从平台运营入口登录");
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        String token = jwtService.issue(user);
        return new LoginResponse(token, "Bearer", toUser(user));
    }

    /** 平台运营登录：固定虚拟租户，仅 platform_admin。 */
    public LoginResponse platformLogin(LoginRequest req) {
        if (req == null || req.username() == null || req.username().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username 不能为空");
        }
        if (req.password() == null || req.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "password 不能为空");
        }
        IamUserEntity user = repository
                .findByTenantIdAndUsername(PlatformIds.PLATFORM_TENANT_ID, req.username().trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误"));
        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户已禁用");
        }
        if (!PlatformIds.isPlatformAdmin(JwtService.splitRoles(user.getRoles()))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "非平台运营账号");
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        String token = jwtService.issue(user);
        return new LoginResponse(token, "Bearer", toUser(user));
    }

    public AuthUser me(String authorization) {
        return toUser(requireUser(authorization));
    }

    public AuthUser updateProfile(String authorization, UpdateProfileRequest req) {
        IamUserEntity user = requireUser(authorization);
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        if (req.displayName() != null) {
            String name = req.displayName().trim();
            if (name.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名称不能为空");
            }
            if (name.length() > 64) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名称过长");
            }
            user.setDisplayName(name);
        }
        if (req.avatar() != null) {
            String avatar = req.avatar().trim();
            if (avatar.isEmpty()) {
                user.setAvatar(null);
            } else {
                validateAvatar(avatar);
                user.setAvatar(avatar);
            }
        }
        return toUser(repository.save(user));
    }

    public void changePassword(String authorization, ChangePasswordRequest req) {
        if (req == null
                || req.currentPassword() == null
                || req.currentPassword().isBlank()
                || req.newPassword() == null
                || req.newPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写当前密码与新密码");
        }
        String next = req.newPassword().trim();
        if (next.length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码至少 6 位");
        }
        if (next.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码过长");
        }
        IamUserEntity user = requireUser(authorization);
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前密码不正确");
        }
        user.setPasswordHash(passwordEncoder.encode(next));
        repository.save(user);
    }

    public PasswordEncoder passwordEncoder() {
        return passwordEncoder;
    }

    private IamUserEntity requireUser(String authorization) {
        String token = extractBearer(authorization);
        Claims claims = jwtService.parse(token);
        String tenantId = claims.get("tenantId", String.class);
        String username = claims.getSubject();
        IamUserEntity user = repository
                .findByTenantIdAndUsername(tenantId, username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户不存在"));
        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户已禁用");
        }
        return user;
    }

    private static void validateAvatar(String avatar) {
        // 允许 data URL（压缩后的小图）或 http(s) 外链
        if (avatar.startsWith("data:image/")) {
            if (avatar.length() > 300_000) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "头像过大，请选用更小的图片");
            }
            return;
        }
        if ((avatar.startsWith("http://") || avatar.startsWith("https://")) && avatar.length() <= 512) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "头像格式无效");
    }

    private static AuthUser toUser(IamUserEntity user) {
        List<String> roles = JwtService.splitRoles(user.getRoles());
        return new AuthUser(
                user.getId(),
                user.getUsername(),
                user.getDisplayName() == null ? user.getUsername() : user.getDisplayName(),
                user.getAvatar(),
                user.getTenantId(),
                roles);
    }

    private static String extractBearer(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "缺少 Authorization");
        }
        String v = authorization.trim();
        if (v.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return v.substring(7).trim();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization 须为 Bearer token");
    }
}
