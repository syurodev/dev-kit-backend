package com.synx.devkit.replication.application.port.out;

import com.synx.devkit.replication.domain.model.ReplicationOperation;
import java.util.UUID;

/** Reserves durable account capacity in the caller's database transaction. */
public interface ReplicationQuota {
    void reserve(UUID accountId, ReplicationOperation operation);
}
