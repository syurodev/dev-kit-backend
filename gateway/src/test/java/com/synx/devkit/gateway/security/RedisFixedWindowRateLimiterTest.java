package com.synx.devkit.gateway.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisFixedWindowRateLimiterTest {
    private ValueOperations<String, String> valueOperations;
    private StringRedisTemplate redisTemplate;
    private RedisFixedWindowRateLimiter limiter;

    @BeforeEach
    void setUp() {
        valueOperations = mock(ValueOperations.class);
        redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        limiter = new RedisFixedWindowRateLimiter(redisTemplate);
    }

    @Test
    void allowsUpToLimitAndSetsExpiryOnFirstRequest() {
        when(valueOperations.increment("desktop-config:rl:127.0.0.1"))
                .thenReturn(1L, 2L, 60L);

        assertTrue(limiter.allow("desktop-config:rl:127.0.0.1", 60));
        assertTrue(limiter.allow("desktop-config:rl:127.0.0.1", 60));
        assertTrue(limiter.allow("desktop-config:rl:127.0.0.1", 60));

        verify(redisTemplate).expire(eq("desktop-config:rl:127.0.0.1"), eq(Duration.ofMinutes(1)));
    }

    @Test
    void rejectsAfterLimit() {
        when(valueOperations.increment("desktop-config:rl:127.0.0.1")).thenReturn(61L);

        assertFalse(limiter.allow("desktop-config:rl:127.0.0.1", 60));
    }

    @Test
    void failOpenWhenRedisThrows() {
        when(valueOperations.increment("desktop-config:rl:127.0.0.1"))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"));

        assertTrue(limiter.allow("desktop-config:rl:127.0.0.1", 60));
    }

    @Test
    void failOpenWhenIncrementReturnsNull() {
        when(valueOperations.increment("desktop-config:rl:127.0.0.1")).thenReturn(null);

        assertTrue(limiter.allow("desktop-config:rl:127.0.0.1", 60));
    }
}
