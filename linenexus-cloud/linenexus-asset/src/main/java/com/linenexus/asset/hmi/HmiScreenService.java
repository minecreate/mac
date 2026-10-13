package com.linenexus.asset.hmi;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HmiScreenService {

    private final HmiScreenRepository repository;
    private final HmiProjectService projectService;

    public HmiScreenService(HmiScreenRepository repository, HmiProjectService projectService) {
        this.repository = repository;
        this.projectService = projectService;
    }

    @Transactional(readOnly = true)
    public List<HmiScreen> list(String tenantId, String status, String projectId) {
        String tenant = blankToDefault(tenantId, "tenant-dev");
        List<HmiScreenEntity> rows;
        boolean hasProject = projectId != null && !projectId.isBlank();
        boolean hasStatus = status != null && !status.isBlank();
        if (hasProject && hasStatus) {
            rows = repository.findByTenantIdAndProjectIdAndStatusOrderByUpdatedAtDesc(
                    tenant, projectId.trim(), status.trim());
        } else if (hasProject) {
            rows = repository.findByTenantIdAndProjectIdOrderByUpdatedAtDesc(tenant, projectId.trim());
        } else if (hasStatus) {
            rows = repository.findByTenantIdAndStatusOrderByUpdatedAtDesc(tenant, status.trim());
        } else {
            rows = repository.findByTenantIdOrderByUpdatedAtDesc(tenant);
        }
        return rows.stream().map(HmiScreenEntity::toDto).toList();
    }

    @Transactional(readOnly = true)
    public HmiScreen get(String id) {
        return require(id).toDto();
    }

    @Transactional
    public HmiScreen create(HmiScreenRequest req) {
        if (req == null || req.name() == null || req.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name 不能为空");
        }
        String id = blankToNull(req.id());
        if (id == null) {
            id = "hmi-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }
        if (repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "画面已存在: " + id);
        }

        String tenant = blankToDefault(req.tenantId(), "tenant-dev");
        String projectId = blankToNull(req.projectId());
        if (projectId == null) {
            projectId = projectService.ensureDefaultProject(tenant);
        } else {
            projectService.get(projectId);
        }

        HmiScreenEntity entity = new HmiScreenEntity();
        entity.setId(id);
        entity.setTenantId(tenant);
        entity.setProjectId(projectId);
        entity.setName(req.name().trim());
        entity.setVersion(1);
        entity.setStatus("draft");
        entity.setDocumentJson(
                blankToNull(req.documentJson()) == null
                        ? HmiDocuments.emptyDocumentJson()
                        : req.documentJson());
        entity.setUpdatedBy(blankToNull(req.updatedBy()));
        return repository.save(entity).toDto();
    }

    @Transactional
    public HmiScreen update(String id, HmiScreenRequest req) {
        HmiScreenEntity entity = require(id);
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        if (req.name() != null && !req.name().isBlank()) {
            entity.setName(req.name().trim());
        }
        if (req.projectId() != null && !req.projectId().isBlank()) {
            projectService.get(req.projectId().trim());
            entity.setProjectId(req.projectId().trim());
        }
        if (req.documentJson() != null && !req.documentJson().isBlank()) {
            entity.setDocumentJson(req.documentJson());
        }
        if (req.updatedBy() != null) {
            entity.setUpdatedBy(blankToNull(req.updatedBy()));
        }
        // 编辑保存保持 draft；发布走 publish
        entity.setStatus("draft");
        entity.setVersion(entity.getVersion() + 1);
        return repository.save(entity).toDto();
    }

    @Transactional
    public HmiScreen publish(String id, String updatedBy) {
        HmiScreenEntity entity = require(id);
        entity.setStatus("published");
        entity.setUpdatedBy(blankToNull(updatedBy));
        entity.setVersion(entity.getVersion() + 1);
        return repository.save(entity).toDto();
    }

    @Transactional
    public void delete(String id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "画面不存在: " + id);
        }
        projectService.assertScreenDeletable(id);
        repository.deleteById(id);
    }

    private HmiScreenEntity require(String id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "画面不存在: " + id));
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
