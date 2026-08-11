package com.synx.devkit.identity.application.port.in;

import java.time.Instant;

public record DeviceSummary(
        String deviceId, String status, Instant createdAt, Instant lastSeenAt, boolean current) {
}
