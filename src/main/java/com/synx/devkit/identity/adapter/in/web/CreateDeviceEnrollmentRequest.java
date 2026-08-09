package com.synx.devkit.identity.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CreateDeviceEnrollmentRequest(@NotBlank String targetDeviceId) {
}
