package com.mypetadmin.ps_gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TenantContextHeadersGatewayFilterTest {

    private static final String INTERNAL_KEY = "trusted-internal-key";
    private final TenantContextHeadersGatewayFilter filter = new TenantContextHeadersGatewayFilter(INTERNAL_KEY);

    @Test
    void injetaEmpresaDoJwtESubstituiHeadersExternos() {
        String empresaId = "22222222-2222-4222-8222-222222222222";
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/contracts")
                .header(HttpHeaders.AUTHORIZATION, "Bearer external-token")
                .header("X-Internal-Key", "fake-key")
                .header("X-Actor-Empresa-Id", "99999999-9999-4999-8999-999999999999")
                .build();
        ServerWebExchange exchange = MockServerWebExchange.from(request)
                .mutate()
                .principal(Mono.just(authentication(empresaId)))
                .build();

        AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = current -> {
            forwarded.set(current.getRequest());
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getHeaders().getFirst("X-Internal-Key")).isEqualTo(INTERNAL_KEY);
        assertThat(forwarded.get().getHeaders().getFirst("X-Actor-Empresa-Id")).isEqualTo(empresaId);
        assertThat(forwarded.get().getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
    }

    @Test
    void jwtSemEmpresaIdRetorna401ENaoExecutaChain() {
        Jwt jwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of("alg", "HS256"),
                Map.of("sub", "11111111-1111-4111-8111-111111111111"));
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/contracts").build())
                .mutate()
                .principal(Mono.just(new JwtAuthenticationToken(jwt)))
                .build();
        AtomicReference<Boolean> chainCalled = new AtomicReference<>(false);

        StepVerifier.create(filter.filter(exchange, current -> {
            chainCalled.set(true);
            return Mono.empty();
        })).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(chainCalled.get()).isFalse();
    }

    @Test
    void semPrincipalJwtRetorna401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/contracts").build());

        StepVerifier.create(filter.filter(exchange, current -> Mono.empty())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private JwtAuthenticationToken authentication(String empresaId) {
        Jwt jwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of("alg", "HS256"),
                Map.of(
                        "sub", "11111111-1111-4111-8111-111111111111",
                        "empresaId", empresaId));
        return new JwtAuthenticationToken(jwt);
    }
}
