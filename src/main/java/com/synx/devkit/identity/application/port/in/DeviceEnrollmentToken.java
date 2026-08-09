package com.synx.devkit.identity.application.port.in;

import java.time.Instant;

public record DeviceEnrollmentToken(String token, String targetDeviceId, Instant expiresAt) {
}
