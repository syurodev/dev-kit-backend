package com.synx.devkit.identity.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Account(
        UUID id,
        String subject,
        String primaryEmail,
        String username,
        Instant createdAt,
        Instant updatedAt) {
}
