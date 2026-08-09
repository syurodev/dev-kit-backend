package com.synx.devkit.identity.application.port.in;

public record AuthorizeSyncRequestCommand(
        String subject,
        String deviceId,
        long protocolVersion) {
}
