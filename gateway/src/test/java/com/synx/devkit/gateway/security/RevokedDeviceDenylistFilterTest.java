package com.synx.devkit.gateway.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class RevokedDeviceDenylistFilterTest {
    private RevokedDeviceDenylist denylist;
    private RevokedDeviceDenylistFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        denylist = mock(RevokedDeviceDenylist.class);
        filter = new RevokedDeviceDenylistFilter(denylist);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @Test
    void skipsNonSyncPaths() throws Exception {
        when(request.getRequestURI()).thenReturn("/v1/desktop/config");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(denylist, never()).isDenied(any(), any());
    }

    @Test
    void rejectsDeniedDevice() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("keycloak-user-1")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        when(request.getRequestURI()).thenReturn("/v1/sync/session");
        when(request.getHeader(RevokedDeviceDenylistFilter.DEVICE_ID_HEADER)).thenReturn("device-a");
        when(denylist.isDenied("keycloak-user-1", "device-a")).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void allowsWhenNotDenied() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("keycloak-user-1")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        when(request.getRequestURI()).thenReturn("/v1/sync/session");
        when(request.getHeader(RevokedDeviceDenylistFilter.DEVICE_ID_HEADER)).thenReturn("device-a");
        when(denylist.isDenied("keycloak-user-1", "device-a")).thenReturn(false);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void skipsDenylistWhenDeviceHeaderMissing() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("keycloak-user-1")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        when(request.getRequestURI()).thenReturn("/v1/sync/session");
        when(request.getHeader(RevokedDeviceDenylistFilter.DEVICE_ID_HEADER)).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(denylist, never()).isDenied(any(), any());
    }
}
