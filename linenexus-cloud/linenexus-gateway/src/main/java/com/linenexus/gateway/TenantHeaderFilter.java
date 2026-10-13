package com.linenexus.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class TenantHeaderFilter implements GlobalFilter, Ordered {

    static final String HEADER = "X-Tenant-Id";
    static final String DEFAULT_TENANT = "tenant-dev";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String tenant = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (tenant != null && !tenant.isBlank()) {
            return chain.filter(exchange);
        }
        ServerHttpRequest request = exchange.getRequest().mutate().header(HEADER, DEFAULT_TENANT).build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
