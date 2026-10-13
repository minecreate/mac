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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/hmi/projects")
public class HmiProjectController {

    private final HmiProjectService service;

    public HmiProjectController(HmiProjectService service) {
        this.service = service;
    }

    @GetMapping
    public List<HmiProject> list(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId) {
        return service.list(tenantId);
    }

    @GetMapping("/{id}")
    public HmiProject get(@PathVariable String id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HmiProject create(
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestBody HmiProjectRequest req) {
        HmiProjectRequest body = req == null
                ? new HmiProjectRequest(null, tenantId, null, null, null)
                : new HmiProjectRequest(
                        req.id(),
                        req.tenantId() != null ? req.tenantId() : tenantId,
                        req.name(),
                        req.sortOrder(),
                        req.homeScreenId());
        return service.create(body);
    }

    @PutMapping("/{id}")
    public HmiProject update(@PathVariable String id, @RequestBody HmiProjectRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/home-screen")
    public HmiProject setHomeScreen(
            @PathVariable String id, @RequestBody Map<String, String> body) {
        String screenId = body == null ? null : body.get("screenId");
        if (screenId == null || screenId.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "screenId 不能为空");
        }
        return service.setHomeScreen(id, screenId.trim());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
