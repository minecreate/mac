package com.linenexus.asset.edge;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EdgeGatewayRegistry {

    private final EdgeGatewayRepository repository;

    public EdgeGatewayRegistry(EdgeGatewayRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public EdgeGateway register(EdgeGatewayRequest req) {
        EdgeGatewayEntity entity = repository.findById(req.id()).orElseGet(EdgeGatewayEntity::new);
        boolean creating = entity.getId() == null;

        String tenant = blankToDefault(req.tenantId(), creating ? "tenant-dev" : entity.getTenantId());
        String status = blankToDefault(req.status(), creating ? "online" : entity.getStatus());
        String managementUrl = blankToNull(req.managementUrl());
        if (managementUrl == null && !creating) {
            managementUrl = entity.getManagementUrl();
        }
        String name = blankToDefault(req.name(), creating ? req.id() : entity.getName());

        entity.setId(req.id());
        entity.setName(name);
        entity.setTenantId(tenant);
        entity.setStatus(status);
        entity.setManagementUrl(managementUrl);
        if (req.latitude() != null) {
            entity.setLatitude(req.latitude());
        }
        if (req.longitude() != null) {
            entity.setLongitude(req.longitude());
        }
        entity.setLastSeenAt(Instant.now());

        return repository.save(entity).toDto();
    }

    @Transactional(readOnly = true)
    public List<EdgeGateway> listByTenant(String tenantId) {
        String tenant = blankToDefault(tenantId, "tenant-dev");
        return repository.findByTenantIdOrderByIdAsc(tenant).stream().map(EdgeGatewayEntity::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Optional<EdgeGateway> find(String id) {
        return repository.findById(id).map(EdgeGatewayEntity::toDto);
    }

    @Transactional(readOnly = true)
    public EdgeGateway require(String id) {
        return find(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "边端网关不存在: " + id));
    }

    @Transactional
    public void remove(String id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "边端网关不存在: " + id);
        }
        repository.deleteById(id);
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
