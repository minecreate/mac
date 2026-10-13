package com.linenexus.asset.audit;

public record AuditLog(
        String id,
        String tenantId,
        String category,
        String action,
        String result,
        String actorId,
        String actorName,
        String target,
        String summary,
        String detail,
        String clientIp,
        String createdAt) {}
