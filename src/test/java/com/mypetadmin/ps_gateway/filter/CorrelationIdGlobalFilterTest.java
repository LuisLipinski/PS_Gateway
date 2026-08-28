package com.mypetadmin.ps_gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

class CorrelationIdGlobalFilterTest {

    private final CorrelationIdGlobalFilter filter = new CorrelationIdGlobalFilter();

    @Test
    void preservaCorrelationIdUuidValido() {
        String correlationId = UUID.randomUUID().toString();
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        GatewayFilterChain chain = exchange -> {
            captured.set(exchange);
            return Mono.empty();
        };
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/auth/login")
                        .header(CorrelationIdGlobalFilter.HEADER, correlationId)
                        .build());

        filter.filter(exchange, chain).block();

        assertThat(captured.get().getRequest().getHeaders().getFirst(CorrelationIdGlobalFilter.HEADER))
                .isEqualTo(correlationId);
        assertThat(captured.get().getResponse().getHeaders().getFirst(CorrelationIdGlobalFilter.HEADER))
                .isEqualTo(correlationId);
        assertThat(filter.getOrder()).isEqualTo(Integer.MIN_VALUE);
    }

    @Test
    void geraCorrelationIdQuandoAusente() {
        String generated = filter.resolveCorrelationId(null);
        assertThat(UUID.fromString(generated)).isNotNull();
    }

    @Test
    void substituiCorrelationIdInvalido() {
        String generated = filter.resolveCorrelationId("valor-invalido");
        assertThat(UUID.fromString(generated)).isNotNull();
        assertThat(generated).isNotEqualTo("valor-invalido");
    }
}
