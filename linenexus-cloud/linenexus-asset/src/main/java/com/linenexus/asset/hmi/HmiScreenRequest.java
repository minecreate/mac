package com.linenexus.asset.hmi;

public record HmiScreenRequest(
        String id,
        String tenantId,
        String projectId,
        String name,
        String documentJson,
        String updatedBy,
        String status) {
}
