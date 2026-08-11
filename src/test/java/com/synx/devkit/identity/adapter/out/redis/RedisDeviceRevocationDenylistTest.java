package com.synx.devkit.identity.adapter.out.redis;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisDeviceRevocationDenylistTest {
    private ValueOperations<String, String> valueOperations;
    private StringRedisTemplate redisTemplate;
    private RedisDeviceRevocationDenylist denylist;

    @BeforeEach
    void setUp() {
        valueOperations = mock(ValueOperations.class);
        redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        denylist = new RedisDeviceRevocationDenylist(redisTemplate);
    }

    @Test
    void putWritesKeyWithTtl() {
        Duration ttl = Duration.ofSeconds(60);

        denylist.put("subject-1", "device-a", ttl);

        verify(valueOperations).set(eq("sync:revoked-device:subject-1:device-a"), eq("1"), eq(ttl));
    }

    @Test
    void failOpenWhenRedisThrows() {
        doThrow(new RedisConnectionFailureException("redis unavailable"))
                .when(valueOperations)
                .set(eq("sync:revoked-device:subject-1:device-a"), eq("1"), eq(Duration.ofSeconds(60)));

        denylist.put("subject-1", "device-a", Duration.ofSeconds(60));
    }
}
