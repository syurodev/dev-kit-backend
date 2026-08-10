package com.synx.devkit.gateway.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DesktopConfigRateLimitFilterTest {
    private RedisFixedWindowRateLimiter limiter;
    private DesktopConfigRateLimitFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() throws Exception {
        limiter = mock(RedisFixedWindowRateLimiter.class);
        filter = new DesktopConfigRateLimitFilter(limiter, 60);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(new java.io.StringWriter()));
        chain = mock(FilterChain.class);
    }

    @Test
    void skipsNonDesktopConfigPaths() throws Exception {
        when(request.getRequestURI()).thenReturn("/v1/sync/push");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(limiter, never()).allow(any(), any(Integer.class));
    }

    @Test
    void allowsDesktopConfigWhenUnderLimit() throws Exception {
        when(request.getRequestURI()).thenReturn("/v1/desktop/config");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(limiter.allow("desktop-config:rl:127.0.0.1", 60)).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void rejectsDesktopConfigWhenOverLimit() throws Exception {
        when(request.getRequestURI()).thenReturn("/v1/desktop/config");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(limiter.allow("desktop-config:rl:127.0.0.1", 60)).thenReturn(false);

        filter.doFilter(request, response, chain);

        verify(response).setStatus(429);
        verify(response).setHeader("Retry-After", "60");
        verify(chain, never()).doFilter(any(), any());
    }
}
