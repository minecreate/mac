package com.linenexus.asset.hmi;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.linenexus.asset.audit.AuditActions;
import com.linenexus.asset.audit.AuditService;
import com.linenexus.asset.audit.ClientIps;

import jakarta.servlet.http.HttpServletRequest;

/**
 * HMI 画面 API。路径设计为 /hmi/*，经网关 /api/assets/hmi/* 访问，便于日后迁到 monitor。
 */
@RestController
@RequestMapping("/hmi/screens")
public class HmiScreenController {

    private final HmiScreenService service;
    private final AuditService auditService;

    public HmiScreenController(HmiScreenService service, AuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @GetMapping
    public List<HmiScreen> list(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "projectId", required = false) String projectId) {
        return service.list(tenantId, status, projectId);
    }

    @GetMapping("/{id}")
    public HmiScreen get(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HmiScreen create(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestBody HmiScreenRequest req) {
        HmiScreenRequest body = req == null
                ? new HmiScreenRequest(null, tenantId, null, null, null, null, null)
                : new HmiScreenRequest(
                        req.id(),
                        req.tenantId() != null ? req.tenantId() : tenantId,
                        req.projectId(),
                        req.name(),
                        req.documentJson(),
                        req.updatedBy(),
                        req.status());
        return service.create(body);
    }

    @PutMapping("/{id}")
    public HmiScreen update(@PathVariable String id, @RequestBody HmiScreenRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/publish")
    public HmiScreen publish(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName,
            HttpServletRequest http) {
        String updatedBy = body == null ? null : body.get("updatedBy");
        HmiScreen screen = service.publish(id, updatedBy);
        String actor = updatedBy != null && !updatedBy.isBlank()
                ? updatedBy
                : (userName == null || userName.isBlank() ? null : userName);
        auditService.record(
                tenantId != null && !tenantId.isBlank() ? tenantId : screen.tenantId(),
                AuditActions.CAT_CONFIG,
                AuditActions.HMI_PUBLISH,
                AuditActions.SUCCESS,
                userId,
                actor,
                screen.id(),
                "发布组态画面：" + (screen.name() == null ? screen.id() : screen.name()),
                null,
                ClientIps.of(http));
        return screen;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
