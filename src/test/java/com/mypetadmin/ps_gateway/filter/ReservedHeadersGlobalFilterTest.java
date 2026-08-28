package com.mypetadmin.ps_gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

class ReservedHeadersGlobalFilterTest {

    private final ReservedHeadersGlobalFilter filter = new ReservedHeadersGlobalFilter();

    @Test
    void removeHeadersReservadosEPreservaHeadersPublicos() {
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            captured.set(exchange);
            return Mono.empty();
        };
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login")
                        .header("X-Internal-Key", "nao-confiar")
                        .header("X-Actor-User-Id", "fake-user")
                        .header("X-Onboarding-Id", "fake-onboarding")
                        .header("X-User-Id", "fake")
                        .header("X-Empresa-Id", "fake")
                        .header("X-Roles", "MASTER")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .build());

        filter.filter(exchange, chain).block();

        HttpHeaders headers = captured.get().getRequest().getHeaders();
        assertThat(ReservedHeadersGlobalFilter.RESERVED_HEADERS)
                .allSatisfy(header -> assertThat(headers.containsKey(header)).isFalse());
        assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer token");
        assertThat(filter.getOrder()).isEqualTo(Integer.MIN_VALUE + 10);
    }
}
