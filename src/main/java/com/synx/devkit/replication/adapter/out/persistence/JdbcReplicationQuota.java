package com.synx.devkit.replication.adapter.out.persistence;

import com.synx.devkit.bootstrap.configuration.SyncQuotaProperties;
import com.synx.devkit.replication.application.port.out.ReplicationQuota;
import com.synx.devkit.replication.domain.model.ReplicationOperation;
import com.synx.devkit.shared.error.QuotaExceededException;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcReplicationQuota implements ReplicationQuota {
    private static final long ENVELOPE_OVERHEAD_BYTES = 4096;
    private final JdbcClient jdbc;
    private final SyncQuotaProperties properties;

    public JdbcReplicationQuota(JdbcClient jdbc, SyncQuotaProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    @Override
    public void reserve(UUID accountId, ReplicationOperation operation) {
        long requestedBytes = Math.addExact(
                operation.envelope().ciphertext().length,
                ENVELOPE_OVERHEAD_BYTES);
        jdbc.sql("""
                        INSERT INTO account_storage_usage(account_id, operations_used, bytes_used, updated_at)
                        VALUES (:accountId, 0, 0, CURRENT_TIMESTAMP)
                        ON CONFLICT (account_id) DO NOTHING
                        """)
                .param("accountId", accountId)
                .update();

        // The conditional UPDATE both locks the account row and reserves
        // capacity. Concurrent pushes cannot oversubscribe the configured cap.
        boolean reserved = jdbc.sql("""
                        UPDATE account_storage_usage
                        SET operations_used = operations_used + 1,
                            bytes_used = bytes_used + :requestedBytes,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE account_id = :accountId
                          AND operations_used < :maxOperations
                          AND bytes_used <= :maxBytes - :requestedBytes
                        RETURNING account_id
                        """)
                .param("accountId", accountId)
                .param("requestedBytes", requestedBytes)
                .param("maxOperations", properties.getMaxOperations())
                .param("maxBytes", properties.getMaxBytes())
                .query(UUID.class)
                .optional()
                .isPresent();
        if (!reserved) {
            throw new QuotaExceededException();
        }
    }
}
