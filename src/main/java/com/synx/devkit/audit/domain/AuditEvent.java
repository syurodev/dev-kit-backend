package com.synx.devkit.audit.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Safe metadata only; callers must never place token/envelope/ciphertext here. */
public record AuditEvent(
        String requestId,
        UUID accountId,
        String deviceId,
        String eventType,
        Map<String, Object> detail,
        Instant occurredAt) {
    public AuditEvent {
        detail = detail == null ? Map.of() : Map.copyOf(detail);
    }
}
