package com.synx.devkit.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.synx.devkit.bootstrap.security.GatewayClaimsValidator;
import com.synx.devkit.bootstrap.security.RequiredAudienceValidator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class GatewayJwtValidatorsTest {
    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    @Test
    void audienceMustContainBackendAudience() {
        assertFalse(new RequiredAudienceValidator("devkit-sync-api")
                .validate(jwt(List.of("devkit-sync-api"), NOW.plusSeconds(60), "keycloak"))
                .hasErrors());
        assertTrue(new RequiredAudienceValidator("devkit-sync-api")
                .validate(jwt(List.of("another-api"), NOW.plusSeconds(60), "keycloak"))
                .hasErrors());
    }

    @Test
    void upstreamIssuerAndExpiryMustBeTrusted() {
        var validator = new GatewayClaimsValidator(
                "keycloak", Clock.fixed(NOW, ZoneOffset.UTC));
        assertFalse(validator.validate(jwt(List.of("devkit-sync-api"), NOW.plusSeconds(60), "keycloak"))
                .hasErrors());
        assertTrue(validator.validate(jwt(List.of("devkit-sync-api"), NOW.minusSeconds(1), "keycloak"))
                .hasErrors());
        assertTrue(validator.validate(jwt(List.of("devkit-sync-api"), NOW.plusSeconds(60), "other"))
                .hasErrors());
    }

    private static Jwt jwt(List<String> audience, Instant upstreamExpiry, String upstreamIssuer) {
        return new Jwt(
                "signed-token-fixture",
                NOW.minusSeconds(10),
                NOW.plusSeconds(30),
                Map.of("alg", "RS256"),
                Map.of(
                        "sub", "subject-1",
                        "aud", audience,
                        "upstream_iss", upstreamIssuer,
                        "upstream_exp", upstreamExpiry.getEpochSecond()));
    }
}
