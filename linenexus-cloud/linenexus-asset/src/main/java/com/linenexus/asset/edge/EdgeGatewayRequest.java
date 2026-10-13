package com.linenexus.asset.edge;

public record EdgeGatewayRequest(
        String id,
        String name,
        String tenantId,
        String status,
        String managementUrl,
        Double latitude,
        Double longitude) {
}
