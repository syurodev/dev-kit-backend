package com.synx.devkit.gateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rejects sync traffic for devices present on the post-revoke Redis denylist. */
public final class RevokedDeviceDenylistFilter extends OncePerRequestFilter {
    static final String DEVICE_ID_HEADER = "X-DevKit-Device-ID";

    private final RevokedDeviceDenylist denylist;

    public RevokedDeviceDenylistFilter(RevokedDeviceDenylist denylist) {
        this.denylist = denylist;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/v1/sync/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            chain.doFilter(request, response);
            return;
        }

        String deviceId = request.getHeader(DEVICE_ID_HEADER);
        if (deviceId != null
                && !deviceId.isBlank()
                && denylist.isDenied(jwtAuthentication.getToken().getSubject(), deviceId)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        chain.doFilter(request, response);
    }
}
