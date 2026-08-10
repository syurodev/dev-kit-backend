package com.synx.devkit.gateway.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Records a small, non-sensitive reason when the public gateway rejects a JWT.
 *
 * <p>Do not log the exception itself: some lower-level decoder exceptions can
 * contain untrusted request data. The response remains Spring Security's
 * standard bearer challenge and does not disclose this diagnostic to clients.
 */
@Component
public final class GatewayAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private static final Logger LOG = LoggerFactory.getLogger(GatewayAuthenticationEntryPoint.class);
    private final BearerTokenAuthenticationEntryPoint delegate = new BearerTokenAuthenticationEntryPoint();

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException failure) throws IOException {
        LOG.warn("Gateway rejected OIDC token for {} {}: reason={}",
                request.getMethod(), request.getRequestURI(), safeReason(failure));
        delegate.commence(request, response, failure);
    }

    /**
     * Maps framework decoder text to a small allow-list of operational reasons.
     *
     * <p>The original text is deliberately never logged. It may change between
     * Spring Security/Nimbus versions and is not suitable for a public security
     * log, while these categories are enough to distinguish IdP reachability,
     * signing-key rotation and a bad caller token.
     */
    static String safeReason(AuthenticationException failure) {
        if (!(failure instanceof OAuth2AuthenticationException oauthFailure)) {
            return "authentication_failed";
        }
        String description = oauthFailure.getError().getDescription();
        if (description == null) {
            return "invalid_token";
        }
        String normalized = description.toLowerCase(Locale.ROOT);
        if ("required audience is missing".equals(normalized)) {
            return "required_audience_missing";
        }
        if ("token subject is missing".equals(normalized)) {
            return "subject_missing";
        }
        if (normalized.startsWith("jwt expired at ")) {
            return "expired";
        }
        if (normalized.contains("iss claim is not valid")) {
            return "issuer_invalid";
        }
        if (normalized.contains("couldn't retrieve remote jwk set")
                || normalized.contains("could not retrieve remote jwk set")) {
            return "jwks_unavailable";
        }
        if (normalized.contains("no matching key") || normalized.contains("no key found")) {
            return "signing_key_unknown";
        }
        if (normalized.contains("invalid signature") || normalized.contains("signature verification failed")) {
            return "signature_invalid";
        }
        if (normalized.contains("invalid jwt") || normalized.contains("malformed jwt")) {
            return "jwt_malformed";
        }
        return "invalid_token";
    }
}
