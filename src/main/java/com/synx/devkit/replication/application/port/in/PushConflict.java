package com.synx.devkit.replication.application.port.in;

import java.time.Instant;

public record PushConflict(
        String id,
        String recordId,
        String recordType,
        String localChangeKey,
        String remoteChangeKey,
        Instant detectedAt) {
}
