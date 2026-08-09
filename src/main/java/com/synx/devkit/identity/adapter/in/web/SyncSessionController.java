package com.synx.devkit.identity.adapter.in.web;

import com.synx.devkit.identity.adapter.in.security.GatewayIdentityResolver;
import com.synx.devkit.identity.application.port.in.EstablishSyncSessionCommand;
import com.synx.devkit.identity.application.port.in.EstablishSyncSessionUseCase;
import com.synx.devkit.shared.adapter.in.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/sync/session")
public final class SyncSessionController {
    public static final String ENROLLMENT_HEADER = "X-DevKit-Enrollment-Token";
    private final GatewayIdentityResolver identities;
    private final SyncHeaderResolver headers;
    private final EstablishSyncSessionUseCase sessions;

    public SyncSessionController(
            GatewayIdentityResolver identities,
            SyncHeaderResolver headers,
            EstablishSyncSessionUseCase sessions) {
        this.identities = identities;
        this.headers = headers;
        this.sessions = sessions;
    }

    @GetMapping
    public SessionResponse establish(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(SyncHeaderResolver.DEVICE_HEADER) String deviceId,
            @RequestHeader(SyncHeaderResolver.PROTOCOL_HEADER) String protocolText,
            @RequestHeader(value = ENROLLMENT_HEADER, required = false) String enrollmentToken,
            HttpServletRequest request) {
        var identity = identities.resolve(jwt);
        var syncHeaders = headers.resolve(deviceId, protocolText);
        var session = sessions.establish(new EstablishSyncSessionCommand(
                identity.subject(),
                identity.email(),
                identity.preferredUsername(),
                syncHeaders.deviceId(),
                syncHeaders.protocolVersion(),
                enrollmentToken,
                identity.upstreamExpiresAt(),
                requestId(request)));
        return new SessionResponse(
                session.accountId().toString(), session.deviceId(), session.expiresAt());
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return value == null ? "unknown" : value.toString();
    }
}
