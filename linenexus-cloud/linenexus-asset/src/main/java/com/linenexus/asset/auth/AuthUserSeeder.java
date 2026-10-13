package com.linenexus.asset.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class AuthUserSeeder implements ApplicationRunner {

    private final IamUserRepository repository;
    private final AuthService authService;

    public AuthUserSeeder(IamUserRepository repository, AuthService authService) {
        this.repository = repository;
        this.authService = authService;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed("tenant-dev", "u-admin", "admin", "管理员", "admin123", "admin");
        seed("tenant-dev", "u-operator", "operator", "操作员", "op123", "operator");
        seed("tenant-dev", "u-viewer", "viewer", "观察员", "view123", "viewer");
        seed(
                PlatformIds.PLATFORM_TENANT_ID,
                "u-platform",
                "platform",
                "平台运营",
                "platform123",
                PlatformIds.ROLE_PLATFORM_ADMIN);
    }

    private void seed(
            String tenantId, String id, String username, String displayName, String password, String roles) {
        if (repository.findByTenantIdAndUsername(tenantId, username).isPresent()) {
            return;
        }
        IamUserEntity user = new IamUserEntity();
        user.setId(id);
        user.setTenantId(tenantId);
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setPasswordHash(authService.passwordEncoder().encode(password));
        user.setRoles(roles);
        user.setEnabled(true);
        repository.save(user);
    }
}
