package com.synx.devkit.gateway.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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

    private static String safeReason(AuthenticationException failure) {
        if (!(failure instanceof OAuth2AuthenticationException oauthFailure)) {
            return "authentication_failed";
        }
        String description = oauthFailure.getError().getDescription();
        if ("Required audience is missing".equals(description)) {
            return "required_audience_missing";
        }
        if ("Token subject is missing".equals(description)) {
            return "subject_missing";
        }
        if (description != null && description.startsWith("Jwt expired at ")) {
            return "expired";
        }
        if (description != null && description.contains("iss claim is not valid")) {
            return "issuer_invalid";
        }
        return "invalid_token";
    }
}
