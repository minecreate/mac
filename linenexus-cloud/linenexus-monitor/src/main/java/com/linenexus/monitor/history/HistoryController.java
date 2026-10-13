package com.linenexus.monitor.history;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.linenexus.monitor.MonitorApplication;
import com.linenexus.monitor.tdengine.TdengineClient;
import com.linenexus.monitor.tdengine.TdengineClient.HistorySample;

@RestController
@RequestMapping("/monitor/history")
public class HistoryController {

    private final TdengineClient tdengine;

    public HistoryController(TdengineClient tdengine) {
        this.tdengine = tdengine;
    }

    /**
     * 按点查询历史曲线。
     *
     * @param pointIds 逗号分隔的 pointId
     * @param interval 可选降采样秒数（如 60）
     */
    @GetMapping
    public Map<String, Object> history(
            @RequestHeader(value = MonitorApplication.HDR_TENANT, defaultValue = "tenant-dev") String tenantId,
            @RequestParam String gatewayId,
            @RequestParam String deviceId,
            @RequestParam String pointIds,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) Integer interval) {
        if (gatewayId == null || gatewayId.isBlank() || deviceId == null || deviceId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "gatewayId/deviceId 必填");
        }
        Instant fromTs = parseInstant(from, "from");
        Instant toTs = parseInstant(to, "to");
        if (!fromTs.isBefore(toTs)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from 必须早于 to");
        }
        String[] ids = pointIds.split(",");
        List<Map<String, Object>> series = new ArrayList<>();
        try {
            for (String raw : ids) {
                String pointId = raw == null ? "" : raw.trim();
                if (pointId.isEmpty()) {
                    continue;
                }
                List<HistorySample> samples =
                        tdengine.queryHistory(tenantId, gatewayId, deviceId, pointId, fromTs, toTs, interval);
                List<Map<String, Object>> points = new ArrayList<>(samples.size());
                for (HistorySample s : samples) {
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("ts", s.ts());
                    p.put("value", s.vDouble() != null ? s.vDouble() : s.vStr());
                    p.put("vDouble", s.vDouble());
                    p.put("vStr", s.vStr());
                    p.put("quality", s.quality());
                    points.add(p);
                }
                Map<String, Object> serie = new LinkedHashMap<>();
                serie.put("pointId", pointId);
                serie.put("points", points);
                series.add(serie);
            }
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "时序库 TDengine 不可用（默认 :6041）。请在 linenexus-cloud 执行 docker compose up -d 后重试: "
                            + e.getMessage());
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", tenantId);
        body.put("gatewayId", gatewayId);
        body.put("deviceId", deviceId);
        body.put("from", fromTs.toString());
        body.put("to", toTs.toString());
        body.put("interval", interval);
        body.put("series", series);
        return body;
    }

    private static Instant parseInstant(String raw, String name) {
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " 须为 ISO-8601 Instant");
        }
    }
}
