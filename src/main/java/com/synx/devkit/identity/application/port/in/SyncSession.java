package com.synx.devkit.identity.application.port.in;

import java.time.Instant;
import java.util.UUID;

public record SyncSession(UUID accountId, String deviceId, Instant expiresAt) {
}
