package com.mypetadmin.ps_gateway.config;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
    private static final String ALLOWED_ORIGIN = "https://app.mypetadmin.test";
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
        registry.add("app.cors.allowed-origins", () -> ALLOWED_ORIGIN);
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
    void rejeitaPayloadAcimaDoLimiteAntesDoDownstream() {
        String oversizedPayload = "{\"email\":\"" + "a".repeat(70 * 1024) + "@example.com\",\"password\":\"senha\"}";

        client.post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(oversizedPayload)
                .exchange()
                .expectStatus().isEqualTo(413);
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
    void metodosNaoAllowlistedNaoSaoEncaminhadosComoOperacaoValida() throws Exception {
        client.get().uri("/api/auth/login")
                .exchange()
                .expectStatus().value(status -> org.assertj.core.api.Assertions.assertThat(status)
                        .isIn(401, 403, 404, 405));

        String jwt = token(VALID_SECRET, Instant.now().plusSeconds(300));
        client.post().uri("/api/contracts")
                .headers(headers -> headers.setBearerAuth(jwt))
                .exchange()
                .expectStatus().value(status -> org.assertj.core.api.Assertions.assertThat(status)
                        .isIn(403, 404, 405));
    }

    @Test
    void namespaceInternalNuncaEhExpostoPeloGatewayMesmoComJwtValido() throws Exception {
        String jwt = token(VALID_SECRET, Instant.now().plusSeconds(300));

        client.get().uri("/internal/usuarios")
                .headers(headers -> headers.setBearerAuth(jwt))
                .exchange()
                .expectStatus().isForbidden();

        client.post().uri("/internal/onboardings")
                .headers(headers -> headers.setBearerAuth(jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void tentativaDePathTraversalCodificadoNaoAbreNamespaceInterno() throws Exception {
        String jwt = token(VALID_SECRET, Instant.now().plusSeconds(300));

        client.get().uri("/api/users/%2e%2e/%2e%2e/internal/usuarios")
                .headers(headers -> headers.setBearerAuth(jwt))
                .exchange()
                .expectStatus().value(status -> org.assertj.core.api.Assertions.assertThat(status)
                        .isIn(400, 403, 404));
    }

    @Test
    void corsPermiteSomenteOriginConfigurada() {
        client.options().uri("/api/auth/login")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type,X-Correlation-Id")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN)
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600");

        client.options().uri("/api/auth/login")
                .header(HttpHeaders.ORIGIN, "https://malicioso.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isForbidden()
                .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    @Test
    void respostasDoGatewayIncluemSecurityHeadersBasicos() {
        client.get().uri("/version")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().valueEquals("X-Frame-Options", "DENY");
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
