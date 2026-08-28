package com.mypetadmin.ps_gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class TenantContextHeadersGatewayFilter implements GatewayFilter {

    private static final String INTERNAL_KEY_HEADER = "X-Internal-Key";
    private static final String ACTOR_EMPRESA_ID_HEADER = "X-Actor-Empresa-Id";

    private final String internalKey;

    public TenantContextHeadersGatewayFilter(@Value("${app.internal-key}") String internalKey) {
        this.internalKey = internalKey;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .flatMap(authentication -> forwardWithTenant(exchange, chain, authentication))
                .switchIfEmpty(unauthorized(exchange));
    }

    private Mono<Void> forwardWithTenant(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            JwtAuthenticationToken authentication) {
        String empresaId = authentication.getToken().getClaimAsString("empresaId");
        if (empresaId == null || empresaId.isBlank()) {
            return unauthorized(exchange);
        }

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(INTERNAL_KEY_HEADER);
                    headers.remove(ACTOR_EMPRESA_ID_HEADER);
                    headers.remove(HttpHeaders.AUTHORIZATION);
                    headers.set(INTERNAL_KEY_HEADER, internalKey);
                    headers.set(ACTOR_EMPRESA_ID_HEADER, empresaId);
                })
                .build();

        return chain.filter(exchange.mutate().request(request).build());
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
