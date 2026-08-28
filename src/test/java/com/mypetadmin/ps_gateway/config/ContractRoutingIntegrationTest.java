package com.mypetadmin.ps_gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
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
class ContractRoutingIntegrationTest {

    private static final String RAW_SECRET = "0123456789abcdef0123456789abcdef";
    private static final String SERVER_INTERNAL_KEY = "gateway-contract-integration-key";
    private static final String EMPRESA_ID = "22222222-2222-4222-8222-222222222222";

    private static final AtomicReference<String> RECEIVED_URI = new AtomicReference<>();
    private static final AtomicReference<String> RECEIVED_INTERNAL_KEY = new AtomicReference<>();
    private static final AtomicReference<String> RECEIVED_EMPRESA = new AtomicReference<>();
    private static final AtomicReference<String> RECEIVED_AUTHORIZATION = new AtomicReference<>();
    private static final AtomicInteger DOWNSTREAM_CALLS = new AtomicInteger();

    private static final DisposableServer CONTRATO_SERVICE = HttpServer.create()
            .port(0)
            .route(routes -> routes.get("/contratos/tenant", (request, response) -> {
                DOWNSTREAM_CALLS.incrementAndGet();
                RECEIVED_URI.set(request.uri());
                RECEIVED_INTERNAL_KEY.set(request.requestHeaders().get("X-Internal-Key"));
                RECEIVED_EMPRESA.set(request.requestHeaders().get("X-Actor-Empresa-Id"));
                RECEIVED_AUTHORIZATION.set(request.requestHeaders().get(HttpHeaders.AUTHORIZATION));
                response.header("X-Actor-Empresa-Id", "must-not-leak");
                response.header("X-Internal-Key", "must-not-leak");
                return response.status(200)
                        .header(HttpHeaders.CONTENT_TYPE, "application/json")
                        .sendString(Mono.just("{\"content\":[]}"));
            }))
            .bindNow();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.services.contrato-url", () -> "http://localhost:" + CONTRATO_SERVICE.port());
        registry.add("app.internal-key", () -> SERVER_INTERNAL_KEY);
    }

    @LocalServerPort
    int port;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        RECEIVED_URI.set(null);
        RECEIVED_INTERNAL_KEY.set(null);
        RECEIVED_EMPRESA.set(null);
        RECEIVED_AUTHORIZATION.set(null);
        DOWNSTREAM_CALLS.set(0);
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @AfterAll
    static void shutdown() {
        CONTRATO_SERVICE.disposeNow();
    }

    @Test
    void getContractsDerivaTenantDoJwtEPreservaFiltros() throws Exception {
        String jwt = token(true);

        client.get().uri(uriBuilder -> uriBuilder
                        .path("/api/contracts")
                        .queryParam("status", "Ativo")
                        .queryParam("page", "0")
                        .build())
                .headers(headers -> headers.setBearerAuth(jwt))
                .header("X-Internal-Key", "client-fake-key")
                .header("X-Actor-Empresa-Id", "99999999-9999-4999-8999-999999999999")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist("X-Internal-Key")
                .expectHeader().doesNotExist("X-Actor-Empresa-Id");

        assertThat(DOWNSTREAM_CALLS.get()).isEqualTo(1);
        assertThat(RECEIVED_URI.get()).contains("/contratos/tenant").contains("status=Ativo").contains("page=0");
        assertThat(RECEIVED_INTERNAL_KEY.get()).isEqualTo(SERVER_INTERNAL_KEY);
        assertThat(RECEIVED_EMPRESA.get()).isEqualTo(EMPRESA_ID);
        assertThat(RECEIVED_AUTHORIZATION.get()).isNull();
    }

    @Test
    void semJwtNaoChegaAoPsContrato() {
        client.get().uri("/api/contracts")
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(DOWNSTREAM_CALLS.get()).isZero();
    }

    @Test
    void jwtSemEmpresaIdNaoChegaAoPsContrato() throws Exception {
        client.get().uri("/api/contracts")
                .headers(headers -> headers.setBearerAuth(token(false)))
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(DOWNSTREAM_CALLS.get()).isZero();
    }

    @Test
    void escritaEmContractsPermaneceNegada() throws Exception {
        client.post().uri("/api/contracts")
                .headers(headers -> headers.setBearerAuth(token(true)))
                .exchange()
                .expectStatus().isForbidden();

        assertThat(DOWNSTREAM_CALLS.get()).isZero();
    }

    private String token(boolean includeEmpresaId) throws Exception {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                .issuer("ps-login")
                .subject("11111111-1111-4111-8111-111111111111")
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .claim("roles", List.of("MASTER"))
                .jwtID("33333333-3333-4333-8333-333333333333");
        if (includeEmpresaId) {
            builder.claim("empresaId", EMPRESA_ID);
        }

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), builder.build());
        jwt.sign(new MACSigner(RAW_SECRET.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
