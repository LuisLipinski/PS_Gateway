package com.mypetadmin.ps_gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

class OnboardingHeadersGatewayFilterTest {

    private final OnboardingHeadersGatewayFilter filter = new OnboardingHeadersGatewayFilter("server-internal-key");

    @Test
    void traduzIdempotencyKeyEInjetaChaveInterna() {
        UUID onboardingId = UUID.randomUUID();
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            captured.set(exchange);
            return Mono.empty();
        };
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/onboardings")
                        .header("Idempotency-Key", onboardingId.toString())
                        .header("X-Internal-Key", "client-value")
                        .header("X-Onboarding-Id", "client-value")
                        .build());

        filter.filter(exchange, chain).block();

        assertThat(captured.get().getRequest().getHeaders().getFirst("Idempotency-Key")).isNull();
        assertThat(captured.get().getRequest().getHeaders().getFirst("X-Onboarding-Id")).isEqualTo(onboardingId.toString());
        assertThat(captured.get().getRequest().getHeaders().getFirst("X-Internal-Key")).isEqualTo("server-internal-key");
    }

    @Test
    void rejeitaChaveAusenteVaziaOuInvalida() {
        assertRejected(null);
        assertRejected("   ");
        assertRejected("not-a-uuid");
        assertThat(filter.parseUuid("  " + UUID.randomUUID() + "  ")).isNotNull();
    }

    private void assertRejected(String value) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.post("/api/onboardings");
        if (value != null) {
            builder.header("Idempotency-Key", value);
        }
        MockServerWebExchange exchange = MockServerWebExchange.from(builder.build());
        GatewayFilterChain chain = ignored -> Mono.error(new AssertionError("downstream nao deveria ser chamado"));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
