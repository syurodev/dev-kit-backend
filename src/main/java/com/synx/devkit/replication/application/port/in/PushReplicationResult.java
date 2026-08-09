package com.synx.devkit.replication.application.port.in;

import java.util.List;

public record PushReplicationResult(
        List<String> sentIdempotencyKeys,
        List<PushConflict> conflicts) {
}
