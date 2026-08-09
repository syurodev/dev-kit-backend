package com.synx.devkit.identity.application.port.in;

import java.time.Instant;

public record EstablishSyncSessionCommand(
        String subject,
        String email,
        String username,
        String deviceId,
        long protocolVersion,
        String enrollmentToken,
        Instant upstreamExpiresAt,
        String requestId) {
}
