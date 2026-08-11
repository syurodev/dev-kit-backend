package com.synx.devkit.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Redis EXISTS check for short-lived post-revoke device blocks. */
public final class RevokedDeviceDenylist {
    private static final Logger log = LoggerFactory.getLogger(RevokedDeviceDenylist.class);

    private final StringRedisTemplate redis;

    public RevokedDeviceDenylist(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean isDenied(String subject, String deviceId) {
        if (subject == null || subject.isBlank() || deviceId == null || deviceId.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(key(subject, deviceId)));
        } catch (Exception exception) {
            log.warn(
                    "Revoked device denylist check failed subject_len={} device_id={}",
                    subject.length(),
                    deviceId,
                    exception);
            return false;
        }
    }

    static String key(String subject, String deviceId) {
        return "sync:revoked-device:" + subject + ":" + deviceId;
    }
}
