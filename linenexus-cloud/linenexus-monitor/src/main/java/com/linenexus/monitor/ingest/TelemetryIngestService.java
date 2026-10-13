package com.linenexus.monitor.ingest;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linenexus.monitor.alarm.AlarmEvaluateService;
import com.linenexus.monitor.gateway.EdgeGatewayRefRepository;
import com.linenexus.monitor.tdengine.TdengineClient;

@Service
public class TelemetryIngestService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryIngestService.class);

    private final ObjectMapper objectMapper;
    private final TdengineClient tdengine;
    private final EdgeGatewayRefRepository gateways;
    private final AlarmEvaluateService alarmEvaluate;

    public TelemetryIngestService(
            ObjectMapper objectMapper,
            TdengineClient tdengine,
            EdgeGatewayRefRepository gateways,
            AlarmEvaluateService alarmEvaluate) {
        this.objectMapper = objectMapper;
        this.tdengine = tdengine;
        this.gateways = gateways;
        this.alarmEvaluate = alarmEvaluate;
    }

    public void ingestJson(String payload) {
        if (payload == null || payload.isBlank()) {
            return;
        }
        TelemetryBatch batch;
        try {
            batch = objectMapper.readValue(payload, TelemetryBatch.class);
        } catch (Exception e) {
            log.warn("invalid telemetry json: {}", e.getMessage());
            return;
        }
        ingest(batch);
    }

    public void ingest(TelemetryBatch batch) {
        if (batch == null || batch.getPoints() == null || batch.getPoints().isEmpty()) {
            return;
        }
        String gatewayId = blankTo(batch.getGatewayId(), "unknown");
        String deviceId = blankTo(batch.getDeviceId(), "unknown");
        String tenantId = gateways
                .findById(gatewayId)
                .map(g -> g.getTenantId())
                .filter(t -> t != null && !t.isBlank())
                .orElse("tenant-dev");

        int ok = 0;
        for (TelemetryPoint p : batch.getPoints()) {
            if (p == null || p.getPointId() == null || p.getPointId().isBlank()) {
                continue;
            }
            Instant ts = parseTs(p.getTs());
            Double vDouble = null;
            String vStr = null;
            JsonNode v = p.getValue();
            if (v != null && !v.isNull()) {
                if (v.isNumber()) {
                    vDouble = v.asDouble();
                } else if (v.isBoolean()) {
                    vDouble = v.asBoolean() ? 1.0 : 0.0;
                    vStr = String.valueOf(v.asBoolean());
                } else if (v.isTextual()) {
                    String text = v.asText();
                    vStr = text;
                    try {
                        vDouble = Double.parseDouble(text.trim());
                    } catch (NumberFormatException ignored) {
                        // keep string only
                    }
                } else {
                    vStr = v.toString();
                }
            }
            try {
                tdengine.writePoint(
                        tenantId,
                        gatewayId,
                        deviceId,
                        p.getPointId(),
                        ts,
                        vDouble,
                        vStr,
                        p.getQuality());
                ok++;
                alarmEvaluate.onPointSample(
                        tenantId, gatewayId, deviceId, p.getPointId(), p.getPointName(), vDouble, ts);
            } catch (Exception e) {
                log.warn(
                        "ingest point {}.{}: {}",
                        deviceId,
                        p.getPointId(),
                        e.getMessage());
            }
        }
        if (ok > 0) {
            log.debug("ingested {} points gw={} device={} tenant={}", ok, gatewayId, deviceId, tenantId);
        }
    }

    private static Instant parseTs(String ts) {
        if (ts == null || ts.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(ts);
        } catch (DateTimeParseException e) {
            return Instant.now();
        }
    }

    private static String blankTo(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }
}
