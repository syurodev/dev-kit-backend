package com.synx.devkit.gateway.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("devkit.gateway.abuse")
public class GatewayAbuseProperties {
    private int ipRequestsPerMinute = 120;
    private int subjectRequestsPerMinute = 300;
    private int maxConcurrentRequests = 64;
    private int maxTrackedKeys = 10_000;

    public int getIpRequestsPerMinute() {
        return ipRequestsPerMinute;
    }

    public void setIpRequestsPerMinute(int value) {
        this.ipRequestsPerMinute = positive(value, "ip requests per minute");
    }

    public int getSubjectRequestsPerMinute() {
        return subjectRequestsPerMinute;
    }

    public void setSubjectRequestsPerMinute(int value) {
        this.subjectRequestsPerMinute = positive(value, "subject requests per minute");
    }

    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    public void setMaxConcurrentRequests(int value) {
        this.maxConcurrentRequests = positive(value, "max concurrent requests");
    }

    public int getMaxTrackedKeys() {
        return maxTrackedKeys;
    }

    public void setMaxTrackedKeys(int value) {
        this.maxTrackedKeys = positive(value, "max tracked rate-limit keys");
    }

    private static int positive(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
