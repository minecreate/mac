package com.linenexus.asset.edge;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
@RequestMapping("/edge-gateways")
public class EdgeGatewayController {

    private final EdgeGatewayRegistry registry;
    private final AuditService auditService;

    public EdgeGatewayController(EdgeGatewayRegistry registry, AuditService auditService) {
        this.registry = registry;
        this.auditService = auditService;
    }

    @GetMapping
    public List<EdgeGateway> list(@RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        return registry.listByTenant(tenantId);
    }

    @GetMapping("/{id}")
    public EdgeGateway get(@PathVariable String id) {
        return registry.require(id);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.OK)
    public EdgeGateway register(
            @RequestBody EdgeGatewayRequest req,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName,
            HttpServletRequest http) {
        if (req == null || req.id() == null || req.id().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id 不能为空");
        }
        EdgeGateway gw = registry.register(req);
        auditService.record(
                tenantId != null && !tenantId.isBlank() ? tenantId : gw.tenantId(),
                AuditActions.CAT_CONFIG,
                AuditActions.GATEWAY_REGISTER,
                AuditActions.SUCCESS,
                userId,
                userName == null || userName.isBlank() ? "system" : userName,
                gw.id(),
                "登记/更新网关：" + (gw.name() == null ? gw.id() : gw.name()),
                null,
                ClientIps.of(http));
        return gw;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable String id,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName,
            HttpServletRequest http) {
        EdgeGateway existing = null;
        try {
            existing = registry.require(id);
        } catch (Exception ignored) {
            // continue delete attempt
        }
        registry.remove(id);
        auditService.record(
                tenantId != null && !tenantId.isBlank()
                        ? tenantId
                        : (existing == null ? "tenant-dev" : existing.tenantId()),
                AuditActions.CAT_CONFIG,
                AuditActions.GATEWAY_DELETE,
                AuditActions.SUCCESS,
                userId,
                userName,
                id,
                "删除网关：" + id,
                null,
                ClientIps.of(http));
    }
}
