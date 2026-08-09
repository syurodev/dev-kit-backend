package com.synx.devkit.replication.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PullResponse(
        @JsonProperty("account_id") String accountId,
        @JsonProperty("device_id") String deviceId,
        @JsonProperty("protocol_version") long protocolVersion,
        List<PushOperationDto> operations,
        @JsonProperty("next_cursor") String nextCursor) {
}
