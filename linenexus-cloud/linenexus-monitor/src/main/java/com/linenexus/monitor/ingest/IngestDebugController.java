package com.linenexus.monitor.ingest;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 无 MQTT 时可用 HTTP 注入一条 TelemetryBatch，便于联调历史曲线。
 */
@RestController
@RequestMapping("/monitor/ingest")
public class IngestDebugController {

    private final TelemetryIngestService ingest;

    public IngestDebugController(TelemetryIngestService ingest) {
        this.ingest = ingest;
    }

    @PostMapping("/telemetry")
    public Map<String, Object> ingest(@RequestBody TelemetryBatch batch) {
        ingest.ingest(batch);
        int n = batch.getPoints() == null ? 0 : batch.getPoints().size();
        return Map.of("ok", true, "points", n);
    }
}
