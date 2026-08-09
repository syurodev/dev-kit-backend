package com.synx.devkit.gateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Limits requests before JWT parsing, protecting both public and authenticated endpoints. */
public final class IpRateLimitFilter extends OncePerRequestFilter {
    private final FixedWindowRateLimiter limiter;
    private final int requestsPerMinute;

    public IpRateLimitFilter(FixedWindowRateLimiter limiter, int requestsPerMinute) {
        this.limiter = limiter;
        this.requestsPerMinute = requestsPerMinute;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        // X-Forwarded-For is intentionally ignored until a trusted edge proxy
        // is configured; accepting it here would let clients rotate identities.
        if (!limiter.allow("ip:" + request.getRemoteAddr(), requestsPerMinute)) {
            GatewayRejectionWriter.rateLimited(response);
            return;
        }
        chain.doFilter(request, response);
    }
}
