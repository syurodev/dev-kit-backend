package com.synx.devkit.identity.adapter.in.web;

import java.time.Instant;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DeviceEnrollmentResponse(String enrollmentToken, String targetDeviceId, Instant expiresAt) {
}
