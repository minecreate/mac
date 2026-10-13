package com.linenexus.asset.hmi;

public record HmiProjectRequest(
        String id, String tenantId, String name, Integer sortOrder, String homeScreenId) {
}
