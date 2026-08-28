package com.mypetadmin.ps_gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
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

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserRoutingIntegrationTest {

    private static final String RAW_SECRET = "0123456789abcdef0123456789abcdef";
    private static final String SERVER_INTERNAL_KEY = "gateway-user-integration-key";
    private static final String ACTOR_USER_ID = "11111111-1111-4111-8111-111111111111";

    private static final AtomicReference<String> RECEIVED_PATH = new AtomicReference<>();
    private static final AtomicReference<String> RECEIVED_INTERNAL_KEY = new AtomicReference<>();
    private static final AtomicReference<String> RECEIVED_ACTOR = new AtomicReference<>();
    private static final AtomicReference<String> RECEIVED_AUTHORIZATION = new AtomicReference<>();
    private static final AtomicInteger DOWNSTREAM_CALLS = new AtomicInteger();

    private static final DisposableServer USER_SERVICE = HttpServer.create()
            .port(0)
            .route(routes -> routes.route(
                    request -> request.uri().startsWith("/internal/usuarios"),
                    (request, response) -> {
                        DOWNSTREAM_CALLS.incrementAndGet();
                        RECEIVED_PATH.set(request.uri());
                        RECEIVED_INTERNAL_KEY.set(request.requestHeaders().get("X-Internal-Key"));
                        RECEIVED_ACTOR.set(request.requestHeaders().get("X-Actor-User-Id"));
                        RECEIVED_AUTHORIZATION.set(request.requestHeaders().get(HttpHeaders.AUTHORIZATION));
                        response.header("X-Internal-Key", "must-not-leak");
                        response.header("X-Actor-User-Id", "must-not-leak");
                        return response.status(200)
                                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                                .sendString(Mono.just("{\"ok\":true}"));
                    }))
            .bindNow();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.services.user-url", () -> "http://localhost:" + USER_SERVICE.port());
        registry.add("app.internal-key", () -> SERVER_INTERNAL_KEY);
    }

    @LocalServerPort
    int port;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        RECEIVED_PATH.set(null);
        RECEIVED_INTERNAL_KEY.set(null);
        RECEIVED_ACTOR.set(null);
        RECEIVED_AUTHORIZATION.set(null);
        DOWNSTREAM_CALLS.set(0);
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @AfterAll
    static void shutdown() {
        USER_SERVICE.disposeNow();
    }

    @Test
    void rotaRaizDerivaActorDoJwtESubstituiHeadersExternos() throws Exception {
        String jwt = token();

        client.post().uri("/api/users")
                .headers(headers -> headers.setBearerAuth(jwt))
                .header("X-Internal-Key", "client-fake-key")
                .header("X-Actor-User-Id", UUID.randomUUID().toString())
                .bodyValue("{\"nome\":\"Novo Usuario\"}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist("X-Internal-Key")
                .expectHeader().doesNotExist("X-Actor-User-Id");

        assertThat(DOWNSTREAM_CALLS.get()).isEqualTo(1);
        assertThat(RECEIVED_PATH.get()).isEqualTo("/internal/usuarios");
        assertThat(RECEIVED_INTERNAL_KEY.get()).isEqualTo(SERVER_INTERNAL_KEY);
        assertThat(RECEIVED_ACTOR.get()).isEqualTo(ACTOR_USER_ID);
        assertThat(RECEIVED_AUTHORIZATION.get()).isNull();
    }

    @Test
    void rotaAninhadaPreservaSegmentoInterno() throws Exception {
        String targetUserId = "22222222-2222-4222-8222-222222222222";
        String jwt = token();

        client.get().uri("/api/users/{id}", targetUserId)
                .headers(headers -> headers.setBearerAuth(jwt))
                .exchange()
                .expectStatus().isOk();

        assertThat(RECEIVED_PATH.get()).isEqualTo("/internal/usuarios/" + targetUserId);
        assertThat(RECEIVED_ACTOR.get()).isEqualTo(ACTOR_USER_ID);
    }

    @Test
    void semJwtNaoChegaAoPsUser() {
        client.get().uri("/api/users")
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(DOWNSTREAM_CALLS.get()).isZero();
    }

    private String token() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("ps-login")
                .subject(ACTOR_USER_ID)
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .claim("empresaId", "33333333-3333-4333-8333-333333333333")
                .claim("roles", List.of("MASTER"))
                .jwtID("44444444-4444-4444-8444-444444444444")
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(RAW_SECRET.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
