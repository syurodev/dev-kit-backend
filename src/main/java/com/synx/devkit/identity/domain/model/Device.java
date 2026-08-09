package com.synx.devkit.identity.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Device(
        UUID id,
        UUID accountId,
        String deviceId,
        DeviceStatus status,
        long protocolVersion,
        Instant firstSeenAt,
        Instant lastSeenAt) {
}
