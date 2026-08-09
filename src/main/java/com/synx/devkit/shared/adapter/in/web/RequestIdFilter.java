package com.synx.devkit.shared.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Adds a safe correlation ID without trusting an unbounded client value. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class RequestIdFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID_ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";
    private static final String HEADER = "X-Request-ID";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        String requestId = isSafe(supplied) ? supplied : UUID.randomUUID().toString();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(HEADER, requestId);
        try (MDC.MDCCloseable ignored = MDC.putCloseable("request_id", requestId)) {
            filterChain.doFilter(request, response);
        }
    }

    private static boolean isSafe(String value) {
        if (value == null || value.isBlank() || value.length() > 128) {
            return false;
        }
        return value.chars().allMatch(c -> c >= 0x21 && c <= 0x7e);
    }
}
