package com.synx.devkit.replication.application.port.out;

import java.util.Collection;
import java.util.UUID;

public interface ReplicationLock {
    void lockAll(UUID accountId, Collection<String> recordIds);
}
