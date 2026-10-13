package com.linenexus.monitor.ingest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TelemetryPoint {

    private String pointId;
    private String pointName;
    private JsonNode value;
    private String quality;
    private String ts;

    public String getPointId() {
        return pointId;
    }

    public void setPointId(String pointId) {
        this.pointId = pointId;
    }

    public String getPointName() {
        return pointName;
    }

    public void setPointName(String pointName) {
        this.pointName = pointName;
    }

    public JsonNode getValue() {
        return value;
    }

    public void setValue(JsonNode value) {
        this.value = value;
    }

    public String getQuality() {
        return quality;
    }

    public void setQuality(String quality) {
        this.quality = quality;
    }

    public String getTs() {
        return ts;
    }

    public void setTs(String ts) {
        this.ts = ts;
    }
}
