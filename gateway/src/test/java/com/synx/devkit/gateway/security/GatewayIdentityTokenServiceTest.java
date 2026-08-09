package com.synx.devkit.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.SignedJWT;
import com.synx.devkit.gateway.configuration.GatewayTokenProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class GatewayIdentityTokenServiceTest {
    @Test
    void internalTokenNeverOutlivesKeycloakToken() throws Exception {
        Instant now = Instant.parse("2026-08-09T05:00:00Z");
        RSAKey signingKey = new RSAKeyGenerator(2048).keyID("test-gateway-key").generate();

        GatewayTokenProperties properties = new GatewayTokenProperties();
        properties.setIssuer("https://gateway.test");
        properties.setAudience("devkit-sync-api");
        properties.setTtl(Duration.ofSeconds(45));

        Jwt upstream = new Jwt(
                "external-token",
                now.minusSeconds(5),
                now.plusSeconds(12),
                Map.of("alg", "RS256"),
                Map.of(
                        "iss", "https://keycloak.test/realms/devkit",
                        "sub", "keycloak-user-1",
                        "aud", List.of("devkit-sync-gateway"),
                        "email", "user@example.test"));

        GatewayIdentityTokenService service = new GatewayIdentityTokenService(
                properties, signingKey, Clock.fixed(now, ZoneOffset.UTC));
        SignedJWT internal = SignedJWT.parse(service.mint(upstream));

        assertThat(internal.getJWTClaimsSet().getExpirationTime().toInstant())
                .isEqualTo(now.plusSeconds(12));
        assertThat(internal.getJWTClaimsSet().getIssuer()).isEqualTo("https://gateway.test");
        assertThat(internal.getJWTClaimsSet().getAudience()).containsExactly("devkit-sync-api");
        assertThat(internal.getJWTClaimsSet().getSubject()).isEqualTo("keycloak-user-1");
        assertThat(internal.getJWTClaimsSet().getLongClaim("upstream_exp"))
                .isEqualTo(now.plusSeconds(12).getEpochSecond());
    }
}
