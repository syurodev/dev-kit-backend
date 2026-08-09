package com.synx.devkit.bootstrap.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("devkit.identity.enrollment")
public class DeviceEnrollmentProperties {
    private Duration lifetime = Duration.ofMinutes(10);

    public Duration getLifetime() {
        return lifetime;
    }

    public void setLifetime(Duration lifetime) {
        if (lifetime == null || lifetime.isNegative() || lifetime.isZero() || lifetime.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("device enrollment lifetime must be between 1ns and 1h");
        }
        this.lifetime = lifetime;
    }
}
