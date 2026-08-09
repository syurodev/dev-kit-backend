package com.synx.devkit.gateway.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.synx.devkit.gateway.configuration.GatewayTokenProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/** Mints the minimal, short-lived identity context trusted by the sync backend. */
@Service
public class GatewayIdentityTokenService {
    private final GatewayTokenProperties properties;
    private final RSAKey signingKey;
    private final Clock clock;

    public GatewayIdentityTokenService(
            GatewayTokenProperties properties, RSAKey signingKey, Clock clock) {
        this.properties = properties;
        this.signingKey = signingKey;
        this.clock = clock;
    }

    public String mint(Jwt upstream) {
        Instant now = clock.instant();
        Instant upstreamExpiry = upstream.getExpiresAt();
        if (upstreamExpiry == null || !now.isBefore(upstreamExpiry)) {
            throw new IllegalArgumentException("Upstream token is already expired");
        }

        // Internal identity must never outlive the token that Keycloak issued.
        Instant internalExpiry = now.plus(properties.getTtl());
        if (internalExpiry.isAfter(upstreamExpiry)) {
            internalExpiry = upstreamExpiry;
        }

        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(properties.getIssuer())
                .audience(properties.getAudience())
                .subject(upstream.getSubject())
                .issueTime(Date.from(now))
                .notBeforeTime(Date.from(now))
                .expirationTime(Date.from(internalExpiry))
                .claim("upstream_iss", upstream.getIssuer().toString())
                .claim("upstream_exp", upstreamExpiry.getEpochSecond());

        copyOptionalString(upstream, claims, "email");
        copyOptionalString(upstream, claims, "preferred_username");

        SignedJWT token = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
                claims.build());
        try {
            token.sign(new RSASSASigner(signingKey));
            return token.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Cannot sign gateway identity JWT", exception);
        }
    }

    private static void copyOptionalString(Jwt upstream, JWTClaimsSet.Builder target, String name) {
        String value = upstream.getClaimAsString(name);
        if (value != null && !value.isBlank()) {
            target.claim(name, value);
        }
    }
}
