package com.synx.devkit.replication.domain.model;

public record ReplicationOperation(
        String idempotencyKey,
        OperationType operation,
        ReplicationEnvelope envelope) {
}
