package com.synx.devkit.replication.application.port.in;

public interface PullReplicationUseCase {
    PullReplicationResult pull(PullReplicationQuery query);
}
