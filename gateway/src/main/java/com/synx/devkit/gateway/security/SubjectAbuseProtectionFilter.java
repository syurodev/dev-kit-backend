package com.synx.devkit.gateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/** Applies authenticated-subject rate limits and a bounded concurrency budget. */
public final class SubjectAbuseProtectionFilter extends OncePerRequestFilter {
    private final FixedWindowRateLimiter limiter;
    private final int requestsPerMinute;
    private final Semaphore concurrency;

    public SubjectAbuseProtectionFilter(
            FixedWindowRateLimiter limiter,
            int requestsPerMinute,
            int maxConcurrentRequests) {
        this.limiter = limiter;
        this.requestsPerMinute = requestsPerMinute;
        this.concurrency = new Semaphore(maxConcurrentRequests);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt
                && !limiter.allow("subject:" + jwt.getToken().getSubject(), requestsPerMinute)) {
            GatewayRejectionWriter.rateLimited(response);
            return;
        }
        if (!concurrency.tryAcquire()) {
            GatewayRejectionWriter.overloaded(response);
            return;
        }
        try {
            chain.doFilter(request, response);
        } finally {
            concurrency.release();
        }
    }
}
