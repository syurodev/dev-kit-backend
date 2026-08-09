package com.synx.devkit.identity.adapter.in.security;

import com.synx.devkit.bootstrap.security.GatewayClaimsValidator;
import com.synx.devkit.shared.error.ValidationException;
import java.time.Instant;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Maps only the gateway claims needed by the application. */
@Component
public final class GatewayIdentityResolver {
    public GatewayIdentity resolve(Jwt jwt) {
        Instant upstreamExpiry = GatewayClaimsValidator.claimInstant(jwt.getClaim("upstream_exp"));
        if (upstreamExpiry == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new ValidationException("authenticated identity is invalid");
        }
        return new GatewayIdentity(
                jwt.getSubject(),
                upstreamExpiry,
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("preferred_username"));
    }
}
