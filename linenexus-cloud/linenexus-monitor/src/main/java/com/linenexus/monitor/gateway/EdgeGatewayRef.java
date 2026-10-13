package com.linenexus.monitor.gateway;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 只读映射 asset 库 edge_gateway，供 ingest 解析 tenant 与离线扫描。 */
@Entity
@Table(name = "edge_gateway")
public class EdgeGatewayRef {

    @Id
    @Column(length = 64, nullable = false)
    private String id;

    @Column(length = 128, nullable = false)
    private String name;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(length = 32, nullable = false)
    private String status;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }
}
