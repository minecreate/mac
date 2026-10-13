package com.linenexus.monitor.audit;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditWriter {

    private static final Logger log = LoggerFactory.getLogger(AuditWriter.class);

    private final AuditLogRepository repository;

    public AuditWriter(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(
            String tenantId,
            String category,
            String action,
            String result,
            String actorId,
            String actorName,
            String target,
            String summary) {
        try {
            AuditLogEntity e = new AuditLogEntity();
            e.setId("aud-" + UUID.randomUUID().toString().replace("-", ""));
            e.setTenantId(tenantId == null || tenantId.isBlank() ? "tenant-dev" : tenantId.trim());
            e.setCategory(category);
            e.setAction(action);
            e.setResult(result);
            e.setActorId(blankToNull(actorId));
            e.setActorName(blankToNull(actorName));
            e.setTarget(blankToNull(target));
            e.setSummary(summary);
            repository.save(e);
        } catch (Exception ex) {
            log.warn("audit write failed: {}", ex.toString());
        }
    }

    private static String blankToNull(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        return v.trim();
    }
}
