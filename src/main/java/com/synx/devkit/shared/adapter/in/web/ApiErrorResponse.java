package com.synx.devkit.shared.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApiErrorResponse(
        String code,
        String message,
        @JsonProperty("request_id") String requestId) {
}
