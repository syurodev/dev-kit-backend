package com.synx.devkit.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("devkit.desktop.config.abuse")
public class DesktopConfigAbuseProperties {
    private int requestsPerMinute = 60;

    public int getRequestsPerMinute() {
        return requestsPerMinute;
    }

    public void setRequestsPerMinute(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("requests per minute must be positive");
        }
        this.requestsPerMinute = value;
    }
}
