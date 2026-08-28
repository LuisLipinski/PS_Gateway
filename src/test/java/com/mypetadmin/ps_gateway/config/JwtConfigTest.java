package com.mypetadmin.ps_gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import reactor.test.StepVerifier;

class JwtConfigTest {

    private final JwtConfig config = new JwtConfig();

    @Test
    void criaSecretEDecodificaJwtHs256Valido() throws Exception {
        String encoded = encodeSecret("0123456789abcdef0123456789abcdef");
        SecretKey key = config.jwtSecretKey(encoded);
        ReactiveJwtDecoder decoder = config.jwtDecoder(key, "ps-login");
        String token = token("0123456789abcdef0123456789abcdef", Instant.now().plusSeconds(300));

        StepVerifier.create(decoder.decode(token))
                .assertNext(jwt -> {
                    assertThat(jwt.getSubject()).isEqualTo("11111111-1111-4111-8111-111111111111");
                    assertThat(jwt.getClaimAsString("iss")).isEqualTo("ps-login");
                    assertThat(jwt.getClaimAsString("empresaId")).isEqualTo("22222222-2222-4222-8222-222222222222");
                })
                .verifyComplete();
    }

    @Test
    void rejeitaJwtExpirado() throws Exception {
        SecretKey key = config.jwtSecretKey(encodeSecret("0123456789abcdef0123456789abcdef"));
        ReactiveJwtDecoder decoder = config.jwtDecoder(key, "ps-login");

        StepVerifier.create(decoder.decode(token("0123456789abcdef0123456789abcdef", Instant.now().minusSeconds(30))))
                .verifyError();
    }

    @Test
    void rejeitaAssinaturaInvalida() throws Exception {
        SecretKey key = config.jwtSecretKey(encodeSecret("0123456789abcdef0123456789abcdef"));
        ReactiveJwtDecoder decoder = config.jwtDecoder(key, "ps-login");

        StepVerifier.create(decoder.decode(token("abcdef0123456789abcdef0123456789", Instant.now().plusSeconds(300))))
                .verifyError();
    }

    @Test
    void rejeitaSecretCurtoEBase64Invalido() {
        assertThatThrownBy(() -> config.jwtSecretKey(Base64.getEncoder().encodeToString("curto".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
        assertThatThrownBy(() -> config.jwtSecretKey("%%%"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    private String token(String rawSecret, Instant expiresAt) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("ps-login")
                .subject("11111111-1111-4111-8111-111111111111")
                .issueTime(new Date())
                .expirationTime(Date.from(expiresAt))
                .claim("empresaId", "22222222-2222-4222-8222-222222222222")
                .claim("roles", java.util.List.of("MASTER"))
                .jwtID("33333333-3333-4333-8333-333333333333")
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(rawSecret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    private String encodeSecret(String rawSecret) {
        return Base64.getEncoder().encodeToString(rawSecret.getBytes(StandardCharsets.UTF_8));
    }
}
