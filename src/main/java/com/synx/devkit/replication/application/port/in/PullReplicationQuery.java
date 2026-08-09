package com.synx.devkit.replication.application.port.in;

import com.synx.devkit.identity.application.port.in.AuthorizedSyncContext;

public record PullReplicationQuery(
        AuthorizedSyncContext context,
        String cursor,
        int limit,
        String requestId) {
}
