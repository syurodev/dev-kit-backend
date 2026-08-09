package com.synx.devkit.gateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Replaces all client identity context before the request reaches the backend. */
@Component
public class GatewayIdentityRelayFilter extends OncePerRequestFilter {
    private static final int MAX_REQUEST_ID_LENGTH = 128;

    private final GatewayIdentityTokenService tokenService;

    public GatewayIdentityRelayFilter(GatewayIdentityTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/v1/sync/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String internalToken = tokenService.mint(jwtAuthentication.getToken());
        String requestId = boundedRequestId(request.getHeader("X-Request-ID"));
        filterChain.doFilter(new TrustedHeadersRequest(request, internalToken, requestId), response);
    }

    private static String boundedRequestId(String candidate) {
        if (candidate == null || candidate.isBlank() || candidate.length() > MAX_REQUEST_ID_LENGTH) {
            return UUID.randomUUID().toString();
        }
        for (int index = 0; index < candidate.length(); index++) {
            char character = candidate.charAt(index);
            if (character < 0x21 || character > 0x7e) {
                return UUID.randomUUID().toString();
            }
        }
        return candidate;
    }

    /** Immutable header view that drops spoofable identity and proxy metadata. */
    private static final class TrustedHeadersRequest extends HttpServletRequestWrapper {
        private final Map<String, List<String>> headers;

        private TrustedHeadersRequest(HttpServletRequest request, String token, String requestId) {
            super(request);
            Map<String, List<String>> sanitized = new LinkedHashMap<>();
            Enumeration<String> names = request.getHeaderNames();
            while (names != null && names.hasMoreElements()) {
                String name = names.nextElement();
                if (!isReserved(name)) {
                    sanitized.put(name, Collections.list(request.getHeaders(name)));
                }
            }
            sanitized.put(HttpHeaders.AUTHORIZATION, List.of("Bearer " + token));
            sanitized.put("X-Request-ID", List.of(requestId));
            this.headers = Collections.unmodifiableMap(sanitized);
        }

        @Override
        public String getHeader(String name) {
            List<String> values = findValues(name);
            return values == null || values.isEmpty() ? null : values.getFirst();
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            List<String> values = findValues(name);
            return Collections.enumeration(values == null ? List.of() : values);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            return Collections.enumeration(new ArrayList<>(headers.keySet()));
        }

        private List<String> findValues(String name) {
            return headers.entrySet().stream()
                    .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }

        private static boolean isReserved(String headerName) {
            String normalized = headerName.toLowerCase(Locale.ROOT);
            return normalized.equals("authorization")
                    || normalized.equals("x-user-id")
                    || normalized.equals("x-account-id")
                    || normalized.equals("x-roles")
                    || normalized.equals("x-request-id")
                    || normalized.equals("forwarded")
                    || normalized.startsWith("x-forwarded-");
        }
    }
}
