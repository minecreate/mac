package com.linenexus.asset.edge;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.linenexus.asset.audit.AuditActions;
import com.linenexus.asset.audit.AuditService;
import com.linenexus.asset.audit.ClientIps;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/edge-gateways/{gatewayId}")
public class EdgeDeviceController {

    private final EdgeDeviceProxy proxy;
    private final AuditService auditService;

    public EdgeDeviceController(EdgeDeviceProxy proxy, AuditService auditService) {
        this.proxy = proxy;
        this.auditService = auditService;
    }

    @GetMapping("/devices")
    public ResponseEntity<byte[]> listDevices(@PathVariable String gatewayId) {
        return proxy.forward(gatewayId, HttpMethod.GET, "/api/v1/devices", null);
    }

    @PostMapping("/devices")
    public ResponseEntity<byte[]> createDevice(
            @PathVariable String gatewayId, @RequestBody(required = false) byte[] body) {
        return proxy.forward(gatewayId, HttpMethod.POST, "/api/v1/devices", body);
    }

    @GetMapping("/devices/{deviceId}")
    public ResponseEntity<byte[]> getDevice(
            @PathVariable String gatewayId, @PathVariable String deviceId) {
        return proxy.forward(gatewayId, HttpMethod.GET, "/api/v1/devices/" + deviceId, null);
    }

    @DeleteMapping("/devices/{deviceId}")
    public ResponseEntity<byte[]> deleteDevice(
            @PathVariable String gatewayId, @PathVariable String deviceId) {
        return proxy.forward(gatewayId, HttpMethod.DELETE, "/api/v1/devices/" + deviceId, null);
    }

    @GetMapping("/devices/{deviceId}/points")
    public ResponseEntity<byte[]> listPoints(
            @PathVariable String gatewayId, @PathVariable String deviceId) {
        return proxy.forward(gatewayId, HttpMethod.GET, "/api/v1/devices/" + deviceId + "/points", null);
    }

    @PostMapping("/devices/{deviceId}/points")
    public ResponseEntity<byte[]> createPoint(
            @PathVariable String gatewayId,
            @PathVariable String deviceId,
            @RequestBody(required = false) byte[] body) {
        return proxy.forward(gatewayId, HttpMethod.POST, "/api/v1/devices/" + deviceId + "/points", body);
    }

    @PutMapping("/points/{pointId}")
    public ResponseEntity<byte[]> updatePoint(
            @PathVariable String gatewayId,
            @PathVariable String pointId,
            @RequestBody(required = false) byte[] body) {
        return proxy.forward(gatewayId, HttpMethod.PUT, "/api/v1/points/" + pointId, body);
    }

    @DeleteMapping("/points/{pointId}")
    public ResponseEntity<byte[]> deletePoint(
            @PathVariable String gatewayId, @PathVariable String pointId) {
        return proxy.forward(gatewayId, HttpMethod.DELETE, "/api/v1/points/" + pointId, null);
    }

    @PostMapping("/devices/{deviceId}/read")
    public ResponseEntity<byte[]> readDevice(
            @PathVariable String gatewayId, @PathVariable String deviceId) {
        return proxy.forward(gatewayId, HttpMethod.POST, "/api/v1/devices/" + deviceId + "/read", null);
    }

    @PostMapping("/devices/{deviceId}/write")
    public ResponseEntity<byte[]> writePoint(
            @PathVariable String gatewayId,
            @PathVariable String deviceId,
            @RequestBody(required = false) byte[] body,
            @RequestHeader(value = "X-Tenant-Id", required = false) String tenantId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName,
            HttpServletRequest http) {
        // 边端写点走 /api/v1/messages，body 需含 deviceId/pointId/value
        ResponseEntity<byte[]> resp = proxy.forward(gatewayId, HttpMethod.POST, "/api/v1/messages", body);
        boolean ok = resp.getStatusCode().is2xxSuccessful();
        String pointHint = extractPointHint(body);
        String target = gatewayId + "/" + deviceId + (pointHint.isEmpty() ? "" : "/" + pointHint);
        auditService.record(
                tenantId,
                AuditActions.CAT_CONTROL,
                AuditActions.POINT_WRITE,
                ok ? AuditActions.SUCCESS : AuditActions.FAILURE,
                userId,
                userName,
                target,
                (ok ? "写点成功：" : "写点失败：") + target,
                body == null ? null : truncate(new String(body, StandardCharsets.UTF_8), 2000),
                ClientIps.of(http));
        return resp;
    }

    @GetMapping("/adapters")
    public ResponseEntity<byte[]> listAdapters(@PathVariable String gatewayId) {
        return proxy.forward(gatewayId, HttpMethod.GET, "/api/v1/adapters", null);
    }

    private static String extractPointHint(byte[] body) {
        if (body == null || body.length == 0) {
            return "";
        }
        String raw = new String(body, StandardCharsets.UTF_8);
        String pointId = jsonField(raw, "pointId");
        if (!pointId.isEmpty()) {
            return pointId;
        }
        return jsonField(raw, "point");
    }

    private static String jsonField(String raw, String key) {
        String token = "\"" + key + "\"";
        int i = raw.indexOf(token);
        if (i < 0) {
            return "";
        }
        int colon = raw.indexOf(':', i + token.length());
        if (colon < 0) {
            return "";
        }
        int q1 = raw.indexOf('"', colon + 1);
        if (q1 < 0) {
            return "";
        }
        int q2 = raw.indexOf('"', q1 + 1);
        if (q2 < 0) {
            return "";
        }
        return raw.substring(q1 + 1, q2);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
