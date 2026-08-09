package com.synx.devkit.replication.adapter.out.persistence;

import com.synx.devkit.replication.application.port.out.ReplicationLock;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Transaction-scoped locks serialize arbitration for the same account/record. */
@Component
public final class PostgresReplicationLock implements ReplicationLock {
    private final JdbcClient jdbc;

    public PostgresReplicationLock(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void lockAll(UUID accountId, Collection<String> recordIds) {
        recordIds.stream()
                .distinct()
                .sorted()
                .map(recordId -> lockKey(accountId, recordId))
                .forEach(key -> jdbc.sql("SELECT pg_advisory_xact_lock(:key)")
                        .param("key", key)
                        // PostgreSQL returns its pseudo-type "void". The row
                        // itself is what proves the lock call completed; its
                        // column value is intentionally ignored.
                        .query((row, rowNumber) -> rowNumber)
                        .single());
    }

    private static long lockKey(UUID accountId, String recordId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(accountId.toString().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(recordId.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(digest.digest(), 0, Long.BYTES).getLong();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
