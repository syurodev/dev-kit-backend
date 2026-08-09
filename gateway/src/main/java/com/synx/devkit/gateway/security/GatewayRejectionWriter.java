package com.synx.devkit.gateway.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

final class GatewayRejectionWriter {
    private GatewayRejectionWriter() {
    }

    static void rateLimited(HttpServletResponse response) throws IOException {
        write(response, 429, "rate_limited", "60");
    }

    static void overloaded(HttpServletResponse response) throws IOException {
        write(response, 503, "gateway_overloaded", "1");
    }

    private static void write(
            HttpServletResponse response,
            int status,
            String code,
            String retryAfter) throws IOException {
        response.setStatus(status);
        response.setHeader("Retry-After", retryAfter);
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"" + code + "\"}");
    }
}
