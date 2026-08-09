package com.synx.devkit.shared.adapter.in.web;

import com.synx.devkit.shared.domain.WireLimits;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Rejects an oversized request before the complete body is held in memory.
 * Declared lengths are rejected immediately; the stream wrapper covers chunked
 * requests whose final size is not known from the headers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public final class BoundedRequestFilter extends OncePerRequestFilter {
    private final ObjectMapper objectMapper;

    public BoundedRequestFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long declared = request.getContentLengthLong();
        if (declared > WireLimits.MAX_REQUEST_BYTES) {
            writeTooLarge(response, request);
            return;
        }
        filterChain.doFilter(new BoundedRequest(request), response);
    }

    private void writeTooLarge(HttpServletResponse response, HttpServletRequest request) throws IOException {
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        String correlationId = requestId == null ? "unknown" : requestId.toString();
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType("application/json");
        response.setHeader("X-Request-ID", correlationId);
        objectMapper.writeValue(response.getOutputStream(), new ApiErrorResponse(
                "payload_too_large",
                "Request body is too large",
                correlationId));
    }

    /** Counts bytes as Jackson reads them, including bodies sent chunked. */
    private static final class BoundedRequest extends HttpServletRequestWrapper {
        private ServletInputStream inputStream;

        private BoundedRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (inputStream == null) {
                inputStream = new BoundedServletInputStream(super.getInputStream());
            }
            return inputStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static final class BoundedServletInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private long bytesRead;

        private BoundedServletInputStream(ServletInputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0) {
                count(1);
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = delegate.read(buffer, offset, length);
            if (read > 0) {
                count(read);
            }
            return read;
        }

        private void count(int amount) {
            bytesRead += amount;
            if (bytesRead > WireLimits.MAX_REQUEST_BYTES) {
                throw new PayloadTooLargeException();
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }
}
