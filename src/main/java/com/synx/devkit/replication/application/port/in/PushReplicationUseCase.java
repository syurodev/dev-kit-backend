package com.synx.devkit.replication.application.port.in;

public interface PushReplicationUseCase {
    PushReplicationResult push(PushReplicationCommand command);
}
