package com.linenexus.asset.hmi;

public record HmiScreen(
        String id,
        String tenantId,
        String projectId,
        String name,
        int version,
        String status,
        String documentJson,
        String updatedBy,
        String createdAt,
        String updatedAt) {
}
