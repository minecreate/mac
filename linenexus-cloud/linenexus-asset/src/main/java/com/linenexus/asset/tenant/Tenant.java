package com.linenexus.asset.tenant;

import java.time.Instant;

public record Tenant(
        String id, String name, String status, String remark, Instant createdAt, Instant updatedAt) {}
