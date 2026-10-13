package com.linenexus.asset.edge;

public record EdgeGateway(
        String id,
        String name,
        String tenantId,
        String status,
        String lastSeenAt,
        String managementUrl,
        Double latitude,
        Double longitude) {
}
