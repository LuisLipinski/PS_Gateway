package com.mypetadmin.ps_gateway.filter;

import java.util.Set;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class ReservedHeadersGlobalFilter implements GlobalFilter, Ordered {

    static final Set<String> RESERVED_HEADERS = Set.of(
            "X-Internal-Key",
            "X-Actor-User-Id",
            "X-Onboarding-Id",
            "X-User-Id",
            "X-Empresa-Id",
            "X-Roles");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> RESERVED_HEADERS.forEach(headers::remove))
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
