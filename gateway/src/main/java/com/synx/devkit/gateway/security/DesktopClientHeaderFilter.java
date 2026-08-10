package com.synx.devkit.gateway.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.web.filter.OncePerRequestFilter;

/** Rejects casual scanners on the public desktop config route. */
public final class DesktopClientHeaderFilter extends OncePerRequestFilter {
    static final String HEADER_NAME = "X-DevKit-Client";
    private static final String CONFIG_PATH = "/v1/desktop/config";
    private static final Pattern CLIENT_HEADER =
            Pattern.compile("^desktop/\\d+\\.\\d+\\.\\d+(-[0-9A-Za-z.-]+)?$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (!CONFIG_PATH.equals(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }
        String clientHeader = request.getHeader(HEADER_NAME);
        if (clientHeader == null || !CLIENT_HEADER.matcher(clientHeader).matches()) {
            GatewayRejectionWriter.invalidClientHeader(response);
            return;
        }
        chain.doFilter(request, response);
    }
}
