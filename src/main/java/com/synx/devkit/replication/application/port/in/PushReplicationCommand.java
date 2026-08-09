package com.synx.devkit.replication.application.port.in;

import com.synx.devkit.identity.application.port.in.AuthorizedSyncContext;
import com.synx.devkit.replication.domain.model.ReplicationOperation;
import java.util.List;

public record PushReplicationCommand(
        AuthorizedSyncContext context,
        List<ReplicationOperation> operations,
        String requestId) {
}
