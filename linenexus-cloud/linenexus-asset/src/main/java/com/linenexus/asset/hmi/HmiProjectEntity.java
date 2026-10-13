package com.linenexus.asset.hmi;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "hmi_project")
public class HmiProjectEntity {

    @Id
    @Column(length = 64, nullable = false)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(length = 128, nullable = false)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "home_screen_id", length = 64)
    private String homeScreenId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public HmiProject toDto() {
        return new HmiProject(
                id,
                tenantId,
                name,
                sortOrder,
                homeScreenId,
                createdAt == null ? null : createdAt.toString(),
                updatedAt == null ? null : updatedAt.toString());
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getHomeScreenId() {
        return homeScreenId;
    }

    public void setHomeScreenId(String homeScreenId) {
        this.homeScreenId = homeScreenId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
