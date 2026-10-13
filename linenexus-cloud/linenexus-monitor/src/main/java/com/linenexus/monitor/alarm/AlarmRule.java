package com.linenexus.monitor.alarm;

public record AlarmRule(
        String id,
        String tenantId,
        String name,
        boolean enabled,
        String ruleType,
        String gatewayId,
        String deviceId,
        String pointId,
        String operator,
        Double threshold,
        Integer offlineSec,
        String severity,
        String createdAt,
        String updatedAt) {}
