package com.synx.devkit.identity.adapter.in.security;

import java.time.Instant;

/** Minimal trusted identity passed from the security adapter to use cases. */
public record GatewayIdentity(
        String subject,
        Instant upstreamExpiresAt,
        String email,
        String preferredUsername) {
}
