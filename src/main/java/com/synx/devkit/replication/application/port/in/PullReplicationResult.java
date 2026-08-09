package com.synx.devkit.replication.application.port.in;

import com.synx.devkit.replication.domain.model.ReplicationOperation;
import java.util.List;

public record PullReplicationResult(
        List<ReplicationOperation> operations,
        String nextCursor) {
}
