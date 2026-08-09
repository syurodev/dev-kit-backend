package com.synx.devkit.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.synx.devkit.shared.adapter.in.web.BoundedRequestFilter;
import com.synx.devkit.shared.adapter.in.web.PayloadTooLargeException;
import com.synx.devkit.shared.adapter.in.web.RequestIdFilter;
import com.synx.devkit.shared.domain.WireLimits;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class BoundedRequestFilterTest {
    private final BoundedRequestFilter filter = new BoundedRequestFilter(JsonMapper.builder().build());

    @Test
    void rejectsDeclaredBodyBeforeReadingIt() throws Exception {
        var request = new MockHttpServletRequest();
        request.setContent(new byte[1]);
        request.setAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "request-123");
        var oversized = new HttpServletRequestWrapper(request) {
            @Override
            public long getContentLengthLong() {
                return WireLimits.MAX_REQUEST_BYTES + 1L;
            }
        };
        var response = new MockHttpServletResponse();

        filter.doFilter(oversized, response, (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("oversized request must not reach the controller");
        });

        assertEquals(413, response.getStatus());
        assertEquals("request-123", response.getHeader("X-Request-ID"));
    }

    @Test
    void countsChunkedBodyWhileItIsRead() throws Exception {
        var request = new MockHttpServletRequest();
        request.setContent(new byte[WireLimits.MAX_REQUEST_BYTES + 1]);
        // A negative length models Transfer-Encoding: chunked, where the final
        // body size is unavailable before the stream is consumed.
        HttpServletRequest chunked = new HttpServletRequestWrapper(request) {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };

        assertThrows(PayloadTooLargeException.class, () -> filter.doFilter(
                chunked,
                new MockHttpServletResponse(),
                (wrapped, ignoredResponse) -> readCompletely((HttpServletRequest) wrapped)));
    }

    private static void readCompletely(HttpServletRequest request) throws IOException {
        byte[] buffer = new byte[8192];
        while (request.getInputStream().read(buffer) >= 0) {
            // Reading is the behavior under test; no body content is retained.
        }
    }
}
