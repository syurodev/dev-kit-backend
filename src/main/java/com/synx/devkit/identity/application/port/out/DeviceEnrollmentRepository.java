package com.synx.devkit.identity.application.port.out;

import java.time.Instant;
import java.util.UUID;

/** Stores only token digests; plaintext enrollment tokens never reach PostgreSQL. */
public interface DeviceEnrollmentRepository {
    void replace(
            UUID accountId,
            String createdByDeviceId,
            String targetDeviceId,
            byte[] tokenDigest,
            Instant expiresAt,
            Instant createdAt);

    boolean consume(
            UUID accountId,
            String targetDeviceId,
            byte[] tokenDigest,
            Instant now);

    int deleteByCreatedByDeviceId(UUID accountId, String createdByDeviceId);
}
