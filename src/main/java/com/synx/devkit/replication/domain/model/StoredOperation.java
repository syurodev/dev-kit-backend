package com.synx.devkit.replication.domain.model;

import java.time.Instant;
import java.util.UUID;

public record StoredOperation(
        long sequence,
        UUID accountId,
        ReplicationOperation operation,
        ContentDigest contentDigest,
        Instant createdAt) {
}
