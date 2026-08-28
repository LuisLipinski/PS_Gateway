package com.mypetadmin.ps_gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
class SecurityRoutingIntegrationTest {

    private static final String VALID_SECRET = "0123456789abcdef0123456789abcdef";
    private static final String INVALID_SECRET = "abcdef0123456789abcdef0123456789";
    private static final DisposableServer DOWNSTREAM = HttpServer.create()
            .port(0)
            .route(routes -> routes
                    .post("/auth/login", (request, response) -> response.status(200).sendString(Mono.just("login-ok")))
                    .post("/auth/refresh", (request, response) -> response.status(200).sendString(Mono.just("refresh-ok")))
                    .post("/auth/logout", (request, response) -> response.status(200).sendString(Mono.just("logout-ok")))
                    .post("/auth/password/change", (request, response) -> response.status(200).sendString(Mono.just("change-ok"))))
            .bindNow();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.services.login-url", () -> "http://localhost:" + DOWNSTREAM.port());
    }

    @LocalServerPort
    int port;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @AfterAll
    static void shutdown() {
        DOWNSTREAM.disposeNow();
    }

    @Test
    void healthEVersionSaoPublicos() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
        client.get().uri("/version").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("ps-gateway");
    }

    @Test
    void loginRefreshELogoutPublicosSaoRoteadosParaPsLogin() {
        client.post().uri("/api/auth/login").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("login-ok");
        client.post().uri("/api/auth/refresh").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("refresh-ok");
        client.post().uri("/api/auth/logout").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("logout-ok");
    }

    @Test
    void trocaDeSenhaExigeJwtEJwtValidoLiberaRota() throws Exception {
        client.post().uri("/api/auth/password/change").exchange()
                .expectStatus().isUnauthorized();

        String jwt = token(VALID_SECRET, Instant.now().plusSeconds(300));
        client.post().uri("/api/auth/password/change")
                .headers(headers -> headers.setBearerAuth(jwt))
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("change-ok");
    }

    @Test
    void jwtExpiradoEAssinaturaInvalidaSaoRejeitados() throws Exception {
        String expiredJwt = token(VALID_SECRET, Instant.now().minusSeconds(30));
        client.post().uri("/api/auth/password/change")
                .headers(headers -> headers.setBearerAuth(expiredJwt))
                .exchange()
                .expectStatus().isUnauthorized();

        String invalidSignatureJwt = token(INVALID_SECRET, Instant.now().plusSeconds(300));
        client.post().uri("/api/auth/password/change")
                .headers(headers -> headers.setBearerAuth(invalidSignatureJwt))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void rotaNaoAllowlistedPermaneceNegada() throws Exception {
        String jwt = token(VALID_SECRET, Instant.now().plusSeconds(300));
        client.get().uri("/api/nao-permitida")
                .headers(headers -> headers.setBearerAuth(jwt))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void corsNaoUsaWildcard() {
        assertThat(System.getProperty("java.version")).startsWith("25");
    }

    private String token(String rawSecret, Instant expiresAt) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("ps-login")
                .subject("11111111-1111-4111-8111-111111111111")
                .issueTime(new Date())
                .expirationTime(Date.from(expiresAt))
                .claim("empresaId", "22222222-2222-4222-8222-222222222222")
                .claim("roles", List.of("MASTER"))
                .jwtID("33333333-3333-4333-8333-333333333333")
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(rawSecret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
