package com.synx.devkit.identity.adapter.out.redis;

import com.synx.devkit.identity.application.port.out.DeviceRevocationDenylist;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public final class RedisDeviceRevocationDenylist implements DeviceRevocationDenylist {
    private static final Logger log = LoggerFactory.getLogger(RedisDeviceRevocationDenylist.class);

    private final StringRedisTemplate redis;

    public RedisDeviceRevocationDenylist(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void put(String subject, String deviceId, Duration ttl) {
        try {
            redis.opsForValue().set(key(subject, deviceId), "1", ttl);
        } catch (Exception exception) {
            log.warn(
                    "Revocation denylist write failed subject_len={} device_id={}",
                    subject == null ? 0 : subject.length(),
                    deviceId,
                    exception);
        }
    }

    static String key(String subject, String deviceId) {
        return "sync:revoked-device:" + subject + ":" + deviceId;
    }
}
