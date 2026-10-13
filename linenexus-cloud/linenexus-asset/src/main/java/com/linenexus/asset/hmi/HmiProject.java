package com.linenexus.asset.hmi;

public record HmiProject(
        String id,
        String tenantId,
        String name,
        int sortOrder,
        String homeScreenId,
        String createdAt,
        String updatedAt) {
}
