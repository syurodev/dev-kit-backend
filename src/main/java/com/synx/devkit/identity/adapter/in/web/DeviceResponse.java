package com.synx.devkit.identity.adapter.in.web;

import java.time.Instant;

public record DeviceResponse(
        String deviceId, String status, Instant createdAt, Instant lastSeenAt, boolean current) {
}
