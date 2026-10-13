package com.linenexus.monitor.alarm;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.linenexus.monitor.MonitorApplication;

@RestController
@RequestMapping("/monitor/alarms")
public class AlarmController {

    private final AlarmService alarms;

    public AlarmController(AlarmService alarms) {
        this.alarms = alarms;
    }

    @GetMapping("/rules")
    public List<AlarmRule> listRules(
            @RequestHeader(value = MonitorApplication.HDR_TENANT, defaultValue = "tenant-dev") String tenantId) {
        return alarms.listRules(tenantId);
    }

    @PostMapping("/rules")
    public AlarmRule createRule(
            @RequestHeader(value = MonitorApplication.HDR_TENANT, defaultValue = "tenant-dev") String tenantId,
            @RequestBody AlarmRuleRequest body) {
        return alarms.createRule(tenantId, body);
    }

    @PutMapping("/rules/{id}")
    public AlarmRule updateRule(
            @RequestHeader(value = MonitorApplication.HDR_TENANT, defaultValue = "tenant-dev") String tenantId,
            @PathVariable String id,
            @RequestBody AlarmRuleRequest body) {
        return alarms.updateRule(tenantId, id, body);
    }

    @DeleteMapping("/rules/{id}")
    public Map<String, Object> deleteRule(
            @RequestHeader(value = MonitorApplication.HDR_TENANT, defaultValue = "tenant-dev") String tenantId,
            @PathVariable String id) {
        alarms.deleteRule(tenantId, id);
        return Map.of("ok", true);
    }

    @GetMapping("/events")
    public List<AlarmEvent> listEvents(
            @RequestHeader(value = MonitorApplication.HDR_TENANT, defaultValue = "tenant-dev") String tenantId,
            @RequestParam(required = false) String status) {
        return alarms.listEvents(tenantId, status);
    }

    @PostMapping("/events/{id}/ack")
    public AlarmEvent ack(
            @RequestHeader(value = MonitorApplication.HDR_TENANT, defaultValue = "tenant-dev") String tenantId,
            @RequestHeader(value = MonitorApplication.HDR_USER_NAME, defaultValue = "") String userName,
            @PathVariable String id) {
        return alarms.ack(tenantId, id, userName);
    }
}
