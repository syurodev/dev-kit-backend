package com.synx.devkit.bootstrap.security;

import com.synx.devkit.shared.domain.WireLimits;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

/** Rejects an oversized Authorization value before JWT parsing/allocation. */
public final class BoundedBearerTokenResolver implements BearerTokenResolver {
    private static final OAuth2Error INVALID = new OAuth2Error(
            "invalid_token", "Bearer token is invalid", null);
    private final DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.length() > WireLimits.MAX_TOKEN_LENGTH + 7) {
            throw new OAuth2AuthenticationException(INVALID);
        }
        return delegate.resolve(request);
    }
}
