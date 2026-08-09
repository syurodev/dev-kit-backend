package com.synx.devkit.replication.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PushResponse(
        @JsonProperty("sent_idempotency_keys") List<String> sentIdempotencyKeys,
        List<ConflictResponse> conflicts) {
}
