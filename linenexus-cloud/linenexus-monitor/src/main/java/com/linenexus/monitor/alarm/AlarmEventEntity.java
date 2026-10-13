package com.linenexus.monitor.alarm;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "alarm_event")
public class AlarmEventEntity {

    @Id
    @Column(length = 64, nullable = false)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "rule_id", length = 64, nullable = false)
    private String ruleId;

    @Column(name = "rule_name", length = 128, nullable = false)
    private String ruleName;

    @Column(name = "rule_type", length = 32, nullable = false)
    private String ruleType;

    @Column(length = 16, nullable = false)
    private String severity = "warning";

    @Column(name = "gateway_id", length = 64)
    private String gatewayId;

    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "point_id", length = 64)
    private String pointId;

    @Column(length = 512, nullable = false)
    private String message;

    @Column(name = "value_snapshot", length = 128)
    private String valueSnapshot;

    /** active | acked */
    @Column(length = 16, nullable = false)
    private String status = "active";

    @Column(name = "triggered_at", nullable = false)
    private Instant triggeredAt;

    @Column(name = "acked_at")
    private Instant ackedAt;

    @Column(name = "acked_by", length = 128)
    private String ackedBy;

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
        if (triggeredAt == null) {
            triggeredAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public AlarmEvent toDto() {
        return new AlarmEvent(
                id,
                tenantId,
                ruleId,
                ruleName,
                ruleType,
                severity,
                gatewayId,
                deviceId,
                pointId,
                message,
                valueSnapshot,
                status,
                triggeredAt == null ? null : triggeredAt.toString(),
                ackedAt == null ? null : ackedAt.toString(),
                ackedBy);
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

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getRuleType() {
        return ruleType;
    }

    public void setRuleType(String ruleType) {
        this.ruleType = ruleType;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getValueSnapshot() {
        return valueSnapshot;
    }

    public void setValueSnapshot(String valueSnapshot) {
        this.valueSnapshot = valueSnapshot;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public void setTriggeredAt(Instant triggeredAt) {
        this.triggeredAt = triggeredAt;
    }

    public Instant getAckedAt() {
        return ackedAt;
    }

    public void setAckedAt(Instant ackedAt) {
        this.ackedAt = ackedAt;
    }

    public String getAckedBy() {
        return ackedBy;
    }

    public void setAckedBy(String ackedBy) {
        this.ackedBy = ackedBy;
    }
}
