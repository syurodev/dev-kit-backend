package com.synx.devkit.gateway.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class FixedWindowRateLimiterTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-08-09T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void rejectsAfterLimitAndBoundsTrackedKeys() {
        var limiter = new FixedWindowRateLimiter(CLOCK, 2);

        assertTrue(limiter.allow("subject:a", 2));
        assertTrue(limiter.allow("subject:a", 2));
        assertFalse(limiter.allow("subject:a", 2));
        assertTrue(limiter.allow("subject:b", 2));
        assertFalse(limiter.allow("subject:c", 2));
    }
}
