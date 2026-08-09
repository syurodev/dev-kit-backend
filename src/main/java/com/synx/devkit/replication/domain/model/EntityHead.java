package com.synx.devkit.replication.domain.model;

import java.time.Instant;
import java.util.UUID;

public record EntityHead(
        UUID accountId,
        String recordId,
        String recordType,
        long currentEntityVersion,
        long headSequence,
        String headIdempotencyKey,
        Instant updatedAt) {
}
