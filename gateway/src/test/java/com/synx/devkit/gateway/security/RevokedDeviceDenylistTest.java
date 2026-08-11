package com.synx.devkit.gateway.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

class RevokedDeviceDenylistTest {
    private static final String SUBJECT = "subject-1";
    private static final String DEVICE_ID = "device-a";
    private static final String KEY = "sync:revoked-device:subject-1:device-a";

    private StringRedisTemplate redis;
    private RevokedDeviceDenylist denylist;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        denylist = new RevokedDeviceDenylist(redis);
    }

    @Test
    void presentKeyBlocks() {
        when(redis.hasKey(eq(KEY))).thenReturn(true);

        assertTrue(denylist.isDenied(SUBJECT, DEVICE_ID));
    }

    @Test
    void absentKeyAllows() {
        when(redis.hasKey(eq(KEY))).thenReturn(false);

        assertFalse(denylist.isDenied(SUBJECT, DEVICE_ID));
    }

    @Test
    void redisErrorFailsOpen() {
        when(redis.hasKey(eq(KEY))).thenThrow(new RedisConnectionFailureException("redis unavailable"));

        assertFalse(denylist.isDenied(SUBJECT, DEVICE_ID));
    }

    @Test
    void blankDeviceIdAllows() {
        assertFalse(denylist.isDenied(SUBJECT, " "));
    }

    @Test
    void blankSubjectAllows() {
        assertFalse(denylist.isDenied(" ", DEVICE_ID));
    }
}
