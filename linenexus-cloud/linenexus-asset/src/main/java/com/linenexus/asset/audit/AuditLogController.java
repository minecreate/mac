package com.linenexus.asset.audit;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/audit-logs")
public class AuditLogController {

    private final AuditService auditService;

    public AuditLogController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-Roles", required = false) String roles,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin(roles);
        Instant fromTs = parseInstant(from);
        Instant toTs = parseInstant(to);
        Page<AuditLog> p =
                auditService.search(tenantId, category, action, result, actor, keyword, fromTs, toTs, page, size);
        return Map.of(
                "items",
                p.getContent(),
                "page",
                p.getNumber(),
                "size",
                p.getSize(),
                "total",
                p.getTotalElements(),
                "totalPages",
                p.getTotalPages());
    }

    @GetMapping("/{id}")
    public AuditLog get(
            @PathVariable String id,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-Roles", required = false) String roles) {
        requireAdmin(roles);
        AuditLog row = auditService.get(tenantId, id);
        if (row == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "日志不存在");
        }
        return row;
    }

    private static void requireAdmin(String roles) {
        if (roles == null || roles.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可查看系统日志");
        }
        for (String r : roles.split(",")) {
            if ("admin".equalsIgnoreCase(r.trim())) {
                return;
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅管理员可查看系统日志");
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(raw.trim());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "时间格式须为 ISO-8601");
        }
    }
}
