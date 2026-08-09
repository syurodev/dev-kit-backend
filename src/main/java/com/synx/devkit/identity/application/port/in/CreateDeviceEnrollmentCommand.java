package com.synx.devkit.identity.application.port.in;

public record CreateDeviceEnrollmentCommand(
        AuthorizedSyncContext context,
        String targetDeviceId,
        String requestId) {
}
