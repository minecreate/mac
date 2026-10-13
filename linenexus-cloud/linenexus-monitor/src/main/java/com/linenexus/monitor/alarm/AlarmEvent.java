package com.linenexus.monitor.alarm;

public record AlarmEvent(
        String id,
        String tenantId,
        String ruleId,
        String ruleName,
        String ruleType,
        String severity,
        String gatewayId,
        String deviceId,
        String pointId,
        String message,
        String valueSnapshot,
        String status,
        String triggeredAt,
        String ackedAt,
        String ackedBy) {}
