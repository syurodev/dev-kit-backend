package com.synx.devkit.replication.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record ConflictResponse(
        String id,
        @JsonProperty("record_id") String recordId,
        @JsonProperty("record_type") String recordType,
        @JsonProperty("local_change_key") String localChangeKey,
        @JsonProperty("remote_change_key") String remoteChangeKey,
        @JsonProperty("detected_at") Instant detectedAt) {
}
