package com.synx.devkit.identity.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record SessionResponse(
        @JsonProperty("account_id") String accountId,
        @JsonProperty("device_id") String deviceId,
        @JsonProperty("expires_at") Instant expiresAt) {
}
