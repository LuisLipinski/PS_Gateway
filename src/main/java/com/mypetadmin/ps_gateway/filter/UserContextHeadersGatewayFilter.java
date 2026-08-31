package com.mypetadmin.ps_gateway.filter;

import java.util.UUID;

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
public class UserContextHeadersGatewayFilter implements GatewayFilter {

    private static final String INTERNAL_KEY_HEADER = "X-Internal-Key";
    private static final String ACTOR_USER_ID_HEADER = "X-Actor-User-Id";
    private static final String ACTOR_EMPRESA_ID_HEADER = "X-Actor-Empresa-Id";
    private static final String EMPRESA_ID_CLAIM = "empresaId";

    private final String internalKey;

    public UserContextHeadersGatewayFilter(@Value("${app.internal-key}") String internalKey) {
        this.internalKey = internalKey;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .filter(JwtAuthenticationToken.class::isInstance)
                .cast(JwtAuthenticationToken.class)
                .flatMap(authentication -> forwardWithTrustedContext(exchange, chain, authentication))
                .switchIfEmpty(unauthorized(exchange));
    }

    private Mono<Void> forwardWithTrustedContext(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            JwtAuthenticationToken authentication) {
        String actorUserId = authentication.getToken().getSubject();
        String actorEmpresaId = authentication.getToken().getClaimAsString(EMPRESA_ID_CLAIM);
        if (!isValidUuid(actorUserId) || !isValidUuid(actorEmpresaId)) {
            return unauthorized(exchange);
        }

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(INTERNAL_KEY_HEADER);
                    headers.remove(ACTOR_USER_ID_HEADER);
                    headers.remove(ACTOR_EMPRESA_ID_HEADER);
                    headers.remove(HttpHeaders.AUTHORIZATION);
                    headers.set(INTERNAL_KEY_HEADER, internalKey);
                    headers.set(ACTOR_USER_ID_HEADER, actorUserId);
                    headers.set(ACTOR_EMPRESA_ID_HEADER, actorEmpresaId);
                })
                .build();

        return chain.filter(exchange.mutate().request(request).build());
    }

    private boolean isValidUuid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
