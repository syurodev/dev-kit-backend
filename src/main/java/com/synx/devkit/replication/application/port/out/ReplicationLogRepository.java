package com.synx.devkit.replication.application.port.out;

import com.synx.devkit.replication.domain.model.ContentDigest;
import com.synx.devkit.replication.domain.model.ReplicationOperation;
import com.synx.devkit.replication.domain.model.StoredOperation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReplicationLogRepository {
    Optional<StoredOperation> findByOperationId(UUID accountId, String operationId);

    Optional<StoredOperation> findByIdempotencyKey(UUID accountId, String idempotencyKey);

    long append(UUID accountId, ReplicationOperation operation, ContentDigest digest, Instant createdAt);

    List<StoredOperation> pageAfter(UUID accountId, long sequence, int limit, int responseBudgetBytes);
}
