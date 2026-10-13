package com.linenexus.asset.tenant;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.linenexus.asset.auth.AuthService;
import com.linenexus.asset.auth.IamUserEntity;
import com.linenexus.asset.auth.IamUserRepository;
import com.linenexus.asset.auth.PlatformIds;

@Service
public class TenantService {

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9\\-_]{1,62}[a-z0-9]$");

    private final TenantRepository tenantRepository;
    private final IamUserRepository userRepository;
    private final AuthService authService;

    public TenantService(
            TenantRepository tenantRepository, IamUserRepository userRepository, AuthService authService) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.authService = authService;
    }

    public List<Tenant> list() {
        return tenantRepository.findAllByOrderByCreatedAtDesc().stream().map(TenantEntity::toRecord).toList();
    }

    public Tenant get(String id) {
        return require(id).toRecord();
    }

    @Transactional
    public Tenant create(TenantCreateRequest req) {
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        String id = normalizeId(req.id());
        String name = requireText(req.name(), "name");
        if (PlatformIds.isPlatformTenant(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "保留租户 ID，不可使用");
        }
        if (tenantRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "租户已存在: " + id);
        }

        TenantEntity entity = new TenantEntity();
        entity.setId(id);
        entity.setName(name);
        entity.setStatus("active");
        entity.setRemark(blankToNull(req.remark()));
        tenantRepository.save(entity);

        if (req.adminUsername() != null && !req.adminUsername().isBlank()) {
            createTenantAdmin(id, req.adminUsername(), req.adminPassword(), req.adminDisplayName());
        }
        return entity.toRecord();
    }

    @Transactional
    public Tenant update(String id, TenantUpdateRequest req) {
        TenantEntity entity = require(id);
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        if (req.name() != null && !req.name().isBlank()) {
            entity.setName(req.name().trim());
        }
        if (req.status() != null && !req.status().isBlank()) {
            String status = req.status().trim().toLowerCase(Locale.ROOT);
            if (!status.equals("active") && !status.equals("disabled")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status 仅支持 active / disabled");
            }
            entity.setStatus(status);
        }
        if (req.remark() != null) {
            entity.setRemark(blankToNull(req.remark()));
        }
        return tenantRepository.save(entity).toRecord();
    }

    private void createTenantAdmin(String tenantId, String username, String password, String displayName) {
        String user = username.trim();
        if (password == null || password.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "创建管理员时 adminPassword 不能为空");
        }
        if (userRepository.findByTenantIdAndUsername(tenantId, user).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "租户管理员用户名已存在");
        }
        IamUserEntity admin = new IamUserEntity();
        admin.setId("u-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        admin.setTenantId(tenantId);
        admin.setUsername(user);
        admin.setDisplayName(
                displayName == null || displayName.isBlank() ? user : displayName.trim());
        admin.setPasswordHash(authService.passwordEncoder().encode(password));
        admin.setRoles("admin");
        admin.setEnabled(true);
        userRepository.save(admin);
    }

    private TenantEntity require(String id) {
        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "租户 id 不能为空");
        }
        return tenantRepository
                .findById(id.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "租户不存在"));
    }

    private static String normalizeId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id 不能为空");
        }
        String id = raw.trim().toLowerCase(Locale.ROOT);
        if (!ID_PATTERN.matcher(id).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "id 须为 3–64 位小写字母/数字/下划线/中划线，且不以符号开头结尾");
        }
        return id;
    }

    private static String requireText(String v, String field) {
        if (v == null || v.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " 不能为空");
        }
        return v.trim();
    }

    private static String blankToNull(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        return v.trim();
    }
}
