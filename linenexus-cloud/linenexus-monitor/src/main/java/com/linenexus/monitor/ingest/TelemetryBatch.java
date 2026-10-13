package com.linenexus.monitor.ingest;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TelemetryBatch {

    private String gatewayId;
    private String deviceId;
    private String deviceName;
    private List<TelemetryPoint> points;

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

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public List<TelemetryPoint> getPoints() {
        return points;
    }

    public void setPoints(List<TelemetryPoint> points) {
        this.points = points;
    }
}
