package com.synx.devkit.identity.application.port.out;

import java.time.Duration;

public interface DeviceRevocationDenylist {
    void put(String subject, String deviceId, Duration ttl);
}
