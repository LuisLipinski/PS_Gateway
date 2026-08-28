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

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class UserContextHeadersGatewayFilterTest {

    private static final String INTERNAL_KEY = "trusted-internal-key";
    private final UserContextHeadersGatewayFilter filter = new UserContextHeadersGatewayFilter(INTERNAL_KEY);

    @Test
    void injetaContextoDerivadoDoJwtERemoveCredenciaisExternas() {
        String actorUserId = "11111111-1111-4111-8111-111111111111";
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer external-token")
                .header("X-Internal-Key", "fake-key")
                .header("X-Actor-User-Id", "99999999-9999-4999-8999-999999999999")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request)
                .mutate()
                .principal(Mono.just(authentication(actorUserId)))
                .build();

        AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = current -> {
            forwarded.set(current.getRequest());
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getHeaders().getFirst("X-Internal-Key")).isEqualTo(INTERNAL_KEY);
        assertThat(forwarded.get().getHeaders().getFirst("X-Actor-User-Id")).isEqualTo(actorUserId);
        assertThat(forwarded.get().getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isNull();
    }

    @Test
    void semPrincipalJwtRetorna401ENaoExecutaChain() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users").build());
        AtomicReference<Boolean> chainCalled = new AtomicReference<>(false);

        StepVerifier.create(filter.filter(exchange, current -> {
            chainCalled.set(true);
            return Mono.empty();
        })).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(chainCalled.get()).isFalse();
    }

    @Test
    void subjectVazioRetorna401() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/users").build())
                .mutate()
                .principal(Mono.just(authentication("")))
                .build();

        StepVerifier.create(filter.filter(exchange, current -> Mono.empty())).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private JwtAuthenticationToken authentication(String subject) {
        Jwt jwt = new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of("alg", "HS256"),
                Map.of("sub", subject));
        return new JwtAuthenticationToken(jwt);
    }
}
