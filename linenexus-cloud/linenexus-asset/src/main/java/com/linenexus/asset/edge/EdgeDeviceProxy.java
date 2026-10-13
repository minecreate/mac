package com.linenexus.asset.edge;

import java.net.URI;
import java.time.Duration;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EdgeDeviceProxy {

    private final EdgeGatewayRegistry registry;
    private final RestClient restClient;

    public EdgeDeviceProxy(EdgeGatewayRegistry registry) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.registry = registry;
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public ResponseEntity<byte[]> forward(String gatewayId, HttpMethod method, String edgePath, byte[] body) {
        EdgeGateway gw = registry.require(gatewayId);
        String base = gw.managementUrl();
        if (base == null || base.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "边端未上报 managementUrl，请检查 edge 配置 cloud.management_url 并重新注册");
        }
        String url = trimSlash(base) + edgePath;
        try {
            RestClient.RequestBodySpec spec = restClient
                    .method(method)
                    .uri(URI.create(url))
                    .accept(MediaType.APPLICATION_JSON);
            ResponseEntity<byte[]> response;
            if (body != null && body.length > 0) {
                response = spec
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .toEntity(byte[].class);
            } else {
                response = spec.retrieve().toEntity(byte[].class);
            }
            return response;
        } catch (RestClientResponseException ex) {
            byte[] errBody = ex.getResponseBodyAsByteArray();
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(errBody);
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "无法连接边端 " + url + ": " + ex.getMessage(),
                    ex);
        }
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
