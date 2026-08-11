package com.synx.devkit.identity.application.port.in;

public record RevokeDeviceCommand(
        AuthorizedSyncContext context, String subject, String targetDeviceId, String requestId) {
}
