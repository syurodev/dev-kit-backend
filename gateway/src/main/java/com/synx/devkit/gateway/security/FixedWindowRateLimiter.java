package com.synx.devkit.gateway.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Small per-instance limiter; production may replace it with a distributed edge limiter. */
public final class FixedWindowRateLimiter {
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong requests = new AtomicLong();
    private final Clock clock;
    private final int maxTrackedKeys;

    public FixedWindowRateLimiter(Clock clock, int maxTrackedKeys) {
        this.clock = clock;
        this.maxTrackedKeys = maxTrackedKeys;
    }

    public boolean allow(String key, int limit) {
        Instant now = clock.instant();
        if (requests.incrementAndGet() % 1024 == 0) {
            windows.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        }
        if (!windows.containsKey(key) && windows.size() >= maxTrackedKeys) {
            // Bound memory even if an attacker can generate many source keys.
            return false;
        }
        var allowed = new boolean[1];
        windows.compute(key, (ignored, current) -> {
            if (current == null || !now.isBefore(current.expiresAt())) {
                allowed[0] = true;
                return new Window(1, now.plus(WINDOW));
            }
            if (current.count() >= limit) {
                return current;
            }
            allowed[0] = true;
            return new Window(current.count() + 1, current.expiresAt());
        });
        return allowed[0];
    }

    private record Window(int count, Instant expiresAt) {
    }
}
