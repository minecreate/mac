package com.linenexus.asset.tenant;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.linenexus.asset.auth.JwtService;
import com.linenexus.asset.auth.PlatformIds;

@RestController
@RequestMapping("/platform/tenants")
public class TenantController {

    private final TenantService service;

    public TenantController(TenantService service) {
        this.service = service;
    }

    @GetMapping
    public List<Tenant> list(@RequestHeader(value = "X-Roles", required = false) String roles) {
        requirePlatformAdmin(roles);
        return service.list();
    }

    @GetMapping("/{id}")
    public Tenant get(
            @PathVariable String id, @RequestHeader(value = "X-Roles", required = false) String roles) {
        requirePlatformAdmin(roles);
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Tenant create(
            @RequestBody TenantCreateRequest req,
            @RequestHeader(value = "X-Roles", required = false) String roles) {
        requirePlatformAdmin(roles);
        return service.create(req);
    }

    @PutMapping("/{id}")
    public Tenant update(
            @PathVariable String id,
            @RequestBody TenantUpdateRequest req,
            @RequestHeader(value = "X-Roles", required = false) String roles) {
        requirePlatformAdmin(roles);
        return service.update(id, req);
    }

    private static void requirePlatformAdmin(String rolesHeader) {
        if (!PlatformIds.isPlatformAdmin(JwtService.splitRoles(rolesHeader))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅平台运营账号可访问");
        }
    }
}
