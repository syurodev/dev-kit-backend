package com.synx.devkit.gateway.security;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Distributed fixed-window limiter backed by Redis INCR + EXPIRE. */
public final class RedisFixedWindowRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(RedisFixedWindowRateLimiter.class);
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redis;

    public RedisFixedWindowRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean allow(String key, int limit) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, WINDOW);
            }
            return count == null || count <= limit;
        } catch (Exception exception) {
            log.warn("Redis rate limit unavailable for key {}; allowing request", key, exception);
            return true;
        }
    }
}
