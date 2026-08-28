package com.mypetadmin.ps_gateway.filter;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class OnboardingHeadersGatewayFilter implements GatewayFilter {

    static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    static final String ONBOARDING_HEADER = "X-Onboarding-Id";
    static final String INTERNAL_KEY_HEADER = "X-Internal-Key";

    private final String internalApiKey;

    public OnboardingHeadersGatewayFilter(@Value("${app.internal-key}") String internalApiKey) {
        this.internalApiKey = internalApiKey;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String rawIdempotencyKey = exchange.getRequest().getHeaders().getFirst(IDEMPOTENCY_HEADER);
        UUID onboardingId = parseUuid(rawIdempotencyKey);
        if (onboardingId == null) {
            return rejectInvalidIdempotencyKey(exchange);
        }

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(IDEMPOTENCY_HEADER);
                    headers.set(ONBOARDING_HEADER, onboardingId.toString());
                    headers.set(INTERNAL_KEY_HEADER, internalApiKey);
                })
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    UUID parseUuid(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(candidate.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Mono<Void> rejectInvalidIdempotencyKey(ServerWebExchange exchange) {
        byte[] body = "{\"code\":\"INVALID_IDEMPOTENCY_KEY\",\"message\":\"Idempotency-Key deve ser um UUID valido.\"}"
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().setContentLength(body.length);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }
}
