package com.linenexus.monitor.alarm;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linenexus.monitor.gateway.EdgeGatewayRef;
import com.linenexus.monitor.gateway.EdgeGatewayRefRepository;

@Service
public class AlarmEvaluateService {

    private static final Logger log = LoggerFactory.getLogger(AlarmEvaluateService.class);

    private final AlarmRuleRepository rules;
    private final AlarmEventRepository events;
    private final EdgeGatewayRefRepository gateways;

    public AlarmEvaluateService(
            AlarmRuleRepository rules, AlarmEventRepository events, EdgeGatewayRefRepository gateways) {
        this.rules = rules;
        this.events = events;
        this.gateways = gateways;
    }

    @Transactional
    public void onPointSample(
            String tenantId,
            String gatewayId,
            String deviceId,
            String pointId,
            String pointName,
            Double value,
            Instant ts) {
        if (value == null) {
            return;
        }
        List<AlarmRuleEntity> matched = rules.findByTenantIdAndEnabledTrueAndRuleType(tenantId, "threshold");
        for (AlarmRuleEntity rule : matched) {
            if (!matchScope(rule, gatewayId, deviceId, pointId)) {
                continue;
            }
            if (!thresholdBreached(rule.getOperator(), value, rule.getThreshold())) {
                continue;
            }
            raiseIfAbsent(
                    rule,
                    gatewayId,
                    deviceId,
                    pointId,
                    "点 " + (pointName == null || pointName.isBlank() ? pointId : pointName)
                            + " 值 " + value + " 触发 "
                            + rule.getOperator() + " " + rule.getThreshold(),
                    String.valueOf(value),
                    ts == null ? Instant.now() : ts);
        }
    }

    @Transactional
    public void scanOffline() {
        List<AlarmRuleEntity> offlineRules = rules.findByEnabledTrueAndRuleType("offline");
        Instant now = Instant.now();
        for (AlarmRuleEntity rule : offlineRules) {
            int sec = rule.getOfflineSec() == null || rule.getOfflineSec() <= 0 ? 60 : rule.getOfflineSec();
            if (rule.getGatewayId() != null && !rule.getGatewayId().isBlank()) {
                gateways.findById(rule.getGatewayId()).ifPresent(gw -> checkGateway(rule, gw, sec, now));
            } else {
                for (EdgeGatewayRef gw : gateways.findByTenantId(rule.getTenantId())) {
                    checkGateway(rule, gw, sec, now);
                }
            }
        }
    }

    private void checkGateway(AlarmRuleEntity rule, EdgeGatewayRef gw, int offlineSec, Instant now) {
        Instant last = gw.getLastSeenAt();
        if (last == null) {
            return;
        }
        long age = now.getEpochSecond() - last.getEpochSecond();
        if (age < offlineSec) {
            return;
        }
        raiseIfAbsent(
                rule,
                gw.getId(),
                null,
                null,
                "网关 " + gw.getName() + " 已超过 " + offlineSec + "s 未心跳（lastSeen=" + last + "）",
                String.valueOf(age),
                now);
    }

    private void raiseIfAbsent(
            AlarmRuleEntity rule,
            String gatewayId,
            String deviceId,
            String pointId,
            String message,
            String valueSnapshot,
            Instant triggeredAt) {
        var existing = events.findFirstByTenantIdAndRuleIdAndStatus(rule.getTenantId(), rule.getId(), "active");
        if (existing.isPresent()) {
            return;
        }
        AlarmEventEntity ev = new AlarmEventEntity();
        ev.setId("alrm-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        ev.setTenantId(rule.getTenantId());
        ev.setRuleId(rule.getId());
        ev.setRuleName(rule.getName());
        ev.setRuleType(rule.getRuleType());
        ev.setSeverity(rule.getSeverity() == null ? "warning" : rule.getSeverity());
        ev.setGatewayId(gatewayId);
        ev.setDeviceId(deviceId);
        ev.setPointId(pointId);
        ev.setMessage(truncate(message, 512));
        ev.setValueSnapshot(truncate(valueSnapshot, 128));
        ev.setStatus("active");
        ev.setTriggeredAt(triggeredAt);
        events.save(ev);
        log.info("alarm raised rule={} msg={}", rule.getId(), message);
    }

    private static boolean matchScope(AlarmRuleEntity rule, String gatewayId, String deviceId, String pointId) {
        if (rule.getGatewayId() != null && !rule.getGatewayId().isBlank() && !rule.getGatewayId().equals(gatewayId)) {
            return false;
        }
        if (rule.getDeviceId() != null && !rule.getDeviceId().isBlank() && !rule.getDeviceId().equals(deviceId)) {
            return false;
        }
        if (rule.getPointId() != null && !rule.getPointId().isBlank() && !rule.getPointId().equals(pointId)) {
            return false;
        }
        return true;
    }

    private static boolean thresholdBreached(String op, double value, Double threshold) {
        if (threshold == null) {
            return false;
        }
        String o = op == null ? "gt" : op.toLowerCase();
        return switch (o) {
            case "gt" -> value > threshold;
            case "gte" -> value >= threshold;
            case "lt" -> value < threshold;
            case "lte" -> value <= threshold;
            case "eq" -> Double.compare(value, threshold) == 0;
            default -> value > threshold;
        };
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
