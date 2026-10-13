package com.linenexus.monitor.alarm;

public class AlarmRuleRequest {

    private String name;
    private Boolean enabled;
    private String ruleType;
    private String gatewayId;
    private String deviceId;
    private String pointId;
    private String operator;
    private Double threshold;
    private Integer offlineSec;
    private String severity;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
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
}
