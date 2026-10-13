package com.linenexus.asset.hmi;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "hmi_screen")
public class HmiScreenEntity {

    @Id
    @Column(length = 64, nullable = false)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "project_id", length = 64)
    private String projectId;

    @Column(length = 128, nullable = false)
    private String name;

    @Column(nullable = false)
    private int version;

    @Column(length = 32, nullable = false)
    private String status;

    @Lob
    @Column(name = "document_json", nullable = false, columnDefinition = "LONGTEXT")
    private String documentJson;

    @Column(name = "updated_by", length = 128)
    private String updatedBy;

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
        if (version <= 0) {
            version = 1;
        }
        if (status == null || status.isBlank()) {
            status = "draft";
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public HmiScreen toDto() {
        return new HmiScreen(
                id,
                tenantId,
                projectId,
                name,
                version,
                status,
                documentJson,
                updatedBy,
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

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDocumentJson() {
        return documentJson;
    }

    public void setDocumentJson(String documentJson) {
        this.documentJson = documentJson;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
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
