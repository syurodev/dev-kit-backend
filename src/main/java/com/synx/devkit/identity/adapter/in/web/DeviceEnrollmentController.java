package com.synx.devkit.identity.adapter.in.web;

import com.synx.devkit.identity.application.port.in.CreateDeviceEnrollmentCommand;
import com.synx.devkit.identity.application.port.in.CreateDeviceEnrollmentUseCase;
import com.synx.devkit.shared.adapter.in.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/sync/devices/enrollments")
public final class DeviceEnrollmentController {
    private final SyncRequestAuthorizer authorizer;
    private final CreateDeviceEnrollmentUseCase enrollments;

    public DeviceEnrollmentController(
            SyncRequestAuthorizer authorizer,
            CreateDeviceEnrollmentUseCase enrollments) {
        this.authorizer = authorizer;
        this.enrollments = enrollments;
    }

    @PostMapping
    public DeviceEnrollmentResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(SyncHeaderResolver.DEVICE_HEADER) String deviceId,
            @RequestHeader(SyncHeaderResolver.PROTOCOL_HEADER) String protocolText,
            @Valid @RequestBody CreateDeviceEnrollmentRequest body,
            HttpServletRequest request) {
        // Authorization is re-checked against the database, so a revoked
        // device cannot mint an enrollment token with only a valid IdP token.
        var context = authorizer.authorize(jwt, deviceId, protocolText);
        var result = enrollments.create(new CreateDeviceEnrollmentCommand(
                context, body.targetDeviceId(), requestId(request)));
        return new DeviceEnrollmentResponse(
                result.token(), result.targetDeviceId(), result.expiresAt());
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return value == null ? "unknown" : value.toString();
    }
}
