package com.synx.devkit.gateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Redis-backed rate limit for the public desktop config endpoint only. */
public final class DesktopConfigRateLimitFilter extends OncePerRequestFilter {
    private static final String CONFIG_PATH = "/v1/desktop/config";
    private static final String KEY_PREFIX = "desktop-config:rl:";

    private final RedisFixedWindowRateLimiter limiter;
    private final int requestsPerMinute;

    public DesktopConfigRateLimitFilter(RedisFixedWindowRateLimiter limiter, int requestsPerMinute) {
        this.limiter = limiter;
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (!CONFIG_PATH.equals(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        // X-Forwarded-For is intentionally ignored until a trusted edge proxy
        // is configured; accepting it here would let clients rotate identities.
        if (!limiter.allow(KEY_PREFIX + request.getRemoteAddr(), requestsPerMinute)) {
            GatewayRejectionWriter.rateLimited(response);
            return;
        }
        chain.doFilter(request, response);
    }
}
