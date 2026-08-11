package com.synx.devkit.identity.adapter.in.web;

import com.synx.devkit.identity.adapter.in.security.GatewayIdentityResolver;
import com.synx.devkit.identity.application.port.in.DeviceSummary;
import com.synx.devkit.identity.application.port.in.ListDevicesCommand;
import com.synx.devkit.identity.application.port.in.ListDevicesUseCase;
import com.synx.devkit.identity.application.port.in.RevokeDeviceCommand;
import com.synx.devkit.identity.application.port.in.RevokeDeviceUseCase;
import com.synx.devkit.shared.adapter.in.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/sync/devices")
public final class DeviceController {
    private final SyncRequestAuthorizer authorizer;
    private final GatewayIdentityResolver identities;
    private final ListDevicesUseCase listDevices;
    private final RevokeDeviceUseCase revokeDevice;

    public DeviceController(
            SyncRequestAuthorizer authorizer,
            GatewayIdentityResolver identities,
            ListDevicesUseCase listDevices,
            RevokeDeviceUseCase revokeDevice) {
        this.authorizer = authorizer;
        this.identities = identities;
        this.listDevices = listDevices;
        this.revokeDevice = revokeDevice;
    }

    @GetMapping
    public DeviceListResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(SyncHeaderResolver.DEVICE_HEADER) String deviceId,
            @RequestHeader(SyncHeaderResolver.PROTOCOL_HEADER) String protocolText) {
        var context = authorizer.authorize(jwt, deviceId, protocolText);
        var summaries = listDevices.list(new ListDevicesCommand(context));
        return new DeviceListResponse(summaries.stream().map(DeviceController::toResponse).toList());
    }

    @PostMapping("/{deviceId}/revoke")
    public void revoke(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(SyncHeaderResolver.DEVICE_HEADER) String callerDeviceId,
            @RequestHeader(SyncHeaderResolver.PROTOCOL_HEADER) String protocolText,
            @PathVariable("deviceId") String targetDeviceId,
            HttpServletRequest request) {
        var context = authorizer.authorize(jwt, callerDeviceId, protocolText);
        var identity = identities.resolve(jwt);
        revokeDevice.revoke(new RevokeDeviceCommand(
                context, identity.subject(), targetDeviceId, requestId(request)));
    }

    private static DeviceResponse toResponse(DeviceSummary summary) {
        return new DeviceResponse(
                summary.deviceId(),
                summary.status(),
                summary.createdAt(),
                summary.lastSeenAt(),
                summary.current());
    }

    private static String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return value == null ? "unknown" : value.toString();
    }
}
