package com.linenexus.asset.audit;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    /** 工业现场一期默认保留 90 天 */
    private static final int RETENTION_DAYS = 90;

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** 写审计失败不影响主流程 */
    public void record(
            String tenantId,
            String category,
            String action,
            String result,
            String actorId,
            String actorName,
            String target,
            String summary,
            String detail,
            String clientIp) {
        try {
            AuditLogEntity e = new AuditLogEntity();
            e.setId("aud-" + UUID.randomUUID().toString().replace("-", ""));
            e.setTenantId(blankTo(tenantId, "tenant-dev"));
            e.setCategory(blankTo(category, "config"));
            e.setAction(blankTo(action, "unknown"));
            e.setResult(blankTo(result, AuditActions.SUCCESS));
            e.setActorId(trimToNull(actorId));
            e.setActorName(trimToNull(actorName));
            e.setTarget(trimTo(target, 256));
            e.setSummary(trimTo(summary, 512));
            e.setDetail(detail);
            e.setClientIp(trimTo(clientIp, 64));
            repository.save(e);
        } catch (Exception ex) {
            log.warn("audit record failed: {}", ex.toString());
        }
    }

    public Page<AuditLog> search(
            String tenantId,
            String category,
            String action,
            String result,
            String actor,
            String keyword,
            Instant fromTs,
            Instant toTs,
            int page,
            int size) {
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), 200);
        PageRequest pr = PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "createdAt"));
        return repository
                .search(
                        blankTo(tenantId, "tenant-dev"),
                        emptyToNull(category),
                        emptyToNull(action),
                        emptyToNull(result),
                        emptyToNull(actor),
                        emptyToNull(keyword),
                        fromTs,
                        toTs,
                        pr)
                .map(this::toDto);
    }

    public AuditLog get(String tenantId, String id) {
        return repository
                .findById(id)
                .filter(e -> blankTo(tenantId, "tenant-dev").equals(e.getTenantId()))
                .map(this::toDto)
                .orElse(null);
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void purgeExpired() {
        Instant before = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        int n = repository.deleteOlderThan(before);
        if (n > 0) {
            log.info("purged {} audit logs older than {} days", n, RETENTION_DAYS);
        }
    }

    private AuditLog toDto(AuditLogEntity e) {
        return new AuditLog(
                e.getId(),
                e.getTenantId(),
                e.getCategory(),
                e.getAction(),
                e.getResult(),
                e.getActorId(),
                e.getActorName(),
                e.getTarget(),
                e.getSummary(),
                e.getDetail(),
                e.getClientIp(),
                e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
    }

    private static String blankTo(String v, String d) {
        return v == null || v.isBlank() ? d : v.trim();
    }

    private static String emptyToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private static String trimTo(String v, int max) {
        String t = trimToNull(v);
        if (t == null) {
            return null;
        }
        return t.length() <= max ? t : t.substring(0, max);
    }
}
