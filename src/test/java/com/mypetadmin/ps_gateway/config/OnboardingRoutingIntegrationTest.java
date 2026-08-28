package com.mypetadmin.ps_gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OnboardingRoutingIntegrationTest {

    private static final String SERVER_INTERNAL_KEY = "gateway-integration-internal-key";
    private static final AtomicReference<String> RECEIVED_INTERNAL_KEY = new AtomicReference<>();
    private static final AtomicReference<String> RECEIVED_ONBOARDING_ID = new AtomicReference<>();
    private static final AtomicReference<String> RECEIVED_IDEMPOTENCY_KEY = new AtomicReference<>();
    private static final AtomicReference<String> RECEIVED_CORRELATION_ID = new AtomicReference<>();
    private static final AtomicInteger DOWNSTREAM_CALLS = new AtomicInteger();

    private static final DisposableServer ORCHESTRATOR = HttpServer.create()
            .port(0)
            .route(routes -> routes.post("/internal/onboardings", (request, response) -> {
                DOWNSTREAM_CALLS.incrementAndGet();
                RECEIVED_INTERNAL_KEY.set(request.requestHeaders().get("X-Internal-Key"));
                RECEIVED_ONBOARDING_ID.set(request.requestHeaders().get("X-Onboarding-Id"));
                RECEIVED_IDEMPOTENCY_KEY.set(request.requestHeaders().get("Idempotency-Key"));
                RECEIVED_CORRELATION_ID.set(request.requestHeaders().get("X-Correlation-Id"));
                response.header("X-Internal-Key", "must-not-leak");
                response.header("X-Onboarding-Id", "must-not-leak");
                return response.status(200)
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .sendString(Mono.just("{\"onboardingId\":\"ok\"}"));
            }))
            .bindNow();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.services.orchestrator-url", () -> "http://localhost:" + ORCHESTRATOR.port());
        registry.add("app.internal-key", () -> SERVER_INTERNAL_KEY);
    }

    @LocalServerPort
    int port;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        RECEIVED_INTERNAL_KEY.set(null);
        RECEIVED_ONBOARDING_ID.set(null);
        RECEIVED_IDEMPOTENCY_KEY.set(null);
        RECEIVED_CORRELATION_ID.set(null);
        DOWNSTREAM_CALLS.set(0);
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @AfterAll
    static void shutdown() {
        ORCHESTRATOR.disposeNow();
    }

    @Test
    void onboardingPublicoTraduzHeadersSemVazarCredenciaisInternas() {
        String idempotencyKey = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        client.post().uri("/api/onboardings")
                .header("Idempotency-Key", idempotencyKey)
                .header("X-Correlation-Id", correlationId)
                .header("X-Internal-Key", "client-must-not-control")
                .header("X-Onboarding-Id", UUID.randomUUID().toString())
                .bodyValue("{\"email\":\"integration@example.invalid\"}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist("X-Internal-Key")
                .expectHeader().doesNotExist("X-Onboarding-Id")
                .expectHeader().valueEquals("X-Correlation-Id", correlationId);

        assertThat(DOWNSTREAM_CALLS.get()).isEqualTo(1);
        assertThat(RECEIVED_INTERNAL_KEY.get()).isEqualTo(SERVER_INTERNAL_KEY);
        assertThat(RECEIVED_ONBOARDING_ID.get()).isEqualTo(idempotencyKey);
        assertThat(RECEIVED_IDEMPOTENCY_KEY.get()).isNull();
        assertThat(RECEIVED_CORRELATION_ID.get()).isEqualTo(correlationId);
    }

    @Test
    void onboardingSemIdempotencyKeyNaoChegaAoOrchestrator() {
        client.post().uri("/api/onboardings")
                .bodyValue("{}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_IDEMPOTENCY_KEY");

        assertThat(DOWNSTREAM_CALLS.get()).isZero();
    }
}
