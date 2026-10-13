package com.linenexus.monitor.alarm;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.linenexus.monitor.audit.AuditWriter;

@Service
public class AlarmService {

    private final AlarmRuleRepository rules;
    private final AlarmEventRepository events;
    private final AuditWriter auditWriter;

    public AlarmService(AlarmRuleRepository rules, AlarmEventRepository events, AuditWriter auditWriter) {
        this.rules = rules;
        this.events = events;
        this.auditWriter = auditWriter;
    }

    public List<AlarmRule> listRules(String tenantId) {
        return rules.findByTenantIdOrderByUpdatedAtDesc(tenantId).stream().map(AlarmRuleEntity::toDto).toList();
    }

    @Transactional
    public AlarmRule createRule(String tenantId, AlarmRuleRequest req) {
        validate(req, true);
        AlarmRuleEntity e = new AlarmRuleEntity();
        e.setId("rule-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        e.setTenantId(tenantId);
        apply(e, req);
        return rules.save(e).toDto();
    }

    @Transactional
    public AlarmRule updateRule(String tenantId, String id, AlarmRuleRequest req) {
        AlarmRuleEntity e = rules.findById(id)
                .filter(r -> tenantId.equals(r.getTenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "规则不存在"));
        validate(req, false);
        apply(e, req);
        return rules.save(e).toDto();
    }

    @Transactional
    public void deleteRule(String tenantId, String id) {
        AlarmRuleEntity e = rules.findById(id)
                .filter(r -> tenantId.equals(r.getTenantId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "规则不存在"));
        rules.delete(e);
    }

    public List<AlarmEvent> listEvents(String tenantId, String status) {
        if (status != null && !status.isBlank()) {
            return events.findByTenantIdAndStatusOrderByTriggeredAtDesc(tenantId, status.trim().toLowerCase(Locale.ROOT))
                    .stream()
                    .map(AlarmEventEntity::toDto)
                    .toList();
        }
        return events.findByTenantIdOrderByTriggeredAtDesc(tenantId).stream()
                .limit(200)
                .map(AlarmEventEntity::toDto)
                .toList();
    }

    @Transactional
    public AlarmEvent ack(String tenantId, String id, String userName) {
        AlarmEventEntity e = events.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "告警不存在"));
        if (!"active".equals(e.getStatus())) {
            return e.toDto();
        }
        String actor = userName == null || userName.isBlank() ? "unknown" : userName;
        e.setStatus("acked");
        e.setAckedAt(Instant.now());
        e.setAckedBy(actor);
        AlarmEvent dto = events.save(e).toDto();
        auditWriter.record(
                tenantId,
                "alarm",
                "alarm_ack",
                "success",
                null,
                actor,
                id,
                "确认告警：" + (e.getMessage() == null ? id : e.getMessage()));
        return dto;
    }

    private static void validate(AlarmRuleRequest req, boolean creating) {
        if (creating && (req.getName() == null || req.getName().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name 必填");
        }
        if (creating && (req.getRuleType() == null || req.getRuleType().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ruleType 必填");
        }
        String type = req.getRuleType() == null ? null : req.getRuleType().trim().toLowerCase(Locale.ROOT);
        if (type != null && !"threshold".equals(type) && !"offline".equals(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ruleType 仅支持 threshold / offline");
        }
        if ("threshold".equals(type)) {
            if (req.getPointId() == null || req.getPointId().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "阈值规则需 pointId");
            }
            if (req.getThreshold() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "阈值规则需 threshold");
            }
        }
        if ("offline".equals(type) && req.getOfflineSec() != null && req.getOfflineSec() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "offlineSec 须为正整数");
        }
    }

    private static void apply(AlarmRuleEntity e, AlarmRuleRequest req) {
        if (req.getName() != null && !req.getName().isBlank()) {
            e.setName(req.getName().trim());
        }
        if (req.getEnabled() != null) {
            e.setEnabled(req.getEnabled());
        }
        if (req.getRuleType() != null && !req.getRuleType().isBlank()) {
            e.setRuleType(req.getRuleType().trim().toLowerCase(Locale.ROOT));
        }
        if (req.getGatewayId() != null) {
            e.setGatewayId(blankToNull(req.getGatewayId()));
        }
        if (req.getDeviceId() != null) {
            e.setDeviceId(blankToNull(req.getDeviceId()));
        }
        if (req.getPointId() != null) {
            e.setPointId(blankToNull(req.getPointId()));
        }
        if (req.getOperator() != null) {
            e.setOperator(blankToNull(req.getOperator()));
        }
        if (e.getOperator() == null && "threshold".equals(e.getRuleType())) {
            e.setOperator("gt");
        }
        if (req.getThreshold() != null) {
            e.setThreshold(req.getThreshold());
        }
        if (req.getOfflineSec() != null) {
            e.setOfflineSec(req.getOfflineSec());
        }
        if (req.getSeverity() != null && !req.getSeverity().isBlank()) {
            e.setSeverity(req.getSeverity().trim());
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
