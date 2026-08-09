package com.synx.devkit.bootstrap.security;

import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** Validates identity provenance and the original Keycloak-token expiry. */
public final class GatewayClaimsValidator implements OAuth2TokenValidator<Jwt> {
    private static final OAuth2Error ERROR = new OAuth2Error(
            "invalid_token", "Gateway identity claims are invalid", null);

    private final String upstreamIssuer;
    private final Clock clock;

    public GatewayClaimsValidator(String upstreamIssuer, Clock clock) {
        this.upstreamIssuer = upstreamIssuer;
        this.clock = clock;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (token.getSubject() == null || token.getSubject().isBlank()) {
            return OAuth2TokenValidatorResult.failure(ERROR);
        }
        if (!upstreamIssuer.equals(token.getClaimAsString("upstream_iss"))) {
            return OAuth2TokenValidatorResult.failure(ERROR);
        }
        Instant upstreamExpiry = claimInstant(token.getClaim("upstream_exp"));
        if (upstreamExpiry == null || !clock.instant().isBefore(upstreamExpiry)) {
            return OAuth2TokenValidatorResult.failure(ERROR);
        }
        return OAuth2TokenValidatorResult.success();
    }

    public static Instant claimInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Number number) {
            return Instant.ofEpochSecond(number.longValue());
        }
        return null;
    }
}
