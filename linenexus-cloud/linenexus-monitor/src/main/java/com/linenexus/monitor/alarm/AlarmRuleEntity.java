package com.linenexus.monitor.alarm;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "alarm_rule")
public class AlarmRuleEntity {

    @Id
    @Column(length = 64, nullable = false)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(length = 128, nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean enabled = true;

    /** threshold | offline */
    @Column(name = "rule_type", length = 32, nullable = false)
    private String ruleType;

    @Column(name = "gateway_id", length = 64)
    private String gatewayId;

    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "point_id", length = 64)
    private String pointId;

    /** gt | gte | lt | lte | eq */
    @Column(length = 16)
    private String operator;

    private Double threshold;

    @Column(name = "offline_sec")
    private Integer offlineSec;

    @Column(length = 16, nullable = false)
    private String severity = "warning";

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

    public AlarmRule toDto() {
        return new AlarmRule(
                id,
                tenantId,
                name,
                enabled,
                ruleType,
                gatewayId,
                deviceId,
                pointId,
                operator,
                threshold,
                offlineSec,
                severity,
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRuleType() {
        return ruleType;
    }

    public void setRuleType(String ruleType) {
        this.ruleType = ruleType;
    }

    public String getGatewayId() {
        return gatewayId;
    }

    public void setGatewayId(String gatewayId) {
        this.gatewayId = gatewayId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getPointId() {
        return pointId;
    }

    public void setPointId(String pointId) {
        this.pointId = pointId;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public Double getThreshold() {
        return threshold;
    }

    public void setThreshold(Double threshold) {
        this.threshold = threshold;
    }

    public Integer getOfflineSec() {
        return offlineSec;
    }

    public void setOfflineSec(Integer offlineSec) {
        this.offlineSec = offlineSec;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
