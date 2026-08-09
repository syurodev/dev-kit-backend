package com.synx.devkit.bootstrap.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("devkit.sync.quota")
public class SyncQuotaProperties {
    private long maxOperations = 1_000_000;
    private long maxBytes = 10L * 1024 * 1024 * 1024;

    public long getMaxOperations() {
        return maxOperations;
    }

    public void setMaxOperations(long maxOperations) {
        if (maxOperations < 1) {
            throw new IllegalArgumentException("sync max operations must be positive");
        }
        this.maxOperations = maxOperations;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(long maxBytes) {
        if (maxBytes < 1) {
            throw new IllegalArgumentException("sync max bytes must be positive");
        }
        this.maxBytes = maxBytes;
    }
}
