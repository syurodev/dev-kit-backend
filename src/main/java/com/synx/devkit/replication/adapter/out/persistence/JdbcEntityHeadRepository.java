package com.synx.devkit.replication.adapter.out.persistence;

import com.synx.devkit.replication.application.port.out.EntityHeadRepository;
import com.synx.devkit.replication.domain.model.EntityHead;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEntityHeadRepository implements EntityHeadRepository {
    private final JdbcClient jdbc;

    public JdbcEntityHeadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<EntityHead> find(UUID accountId, String recordId) {
        return jdbc.sql("""
                        SELECT account_id, record_id, record_type, current_entity_version,
                               head_seq, head_idempotency_key, updated_at
                        FROM entity_heads
                        WHERE account_id = :accountId AND record_id = :recordId
                        """)
                .param("accountId", accountId)
                .param("recordId", recordId)
                .query(this::map)
                .optional();
    }

    @Override
    public void save(EntityHead head) {
        jdbc.sql("""
                        INSERT INTO entity_heads(
                            account_id, record_id, record_type, current_entity_version,
                            head_seq, head_idempotency_key, updated_at)
                        VALUES (
                            :accountId, :recordId, :recordType, :version,
                            :headSeq, :idempotencyKey, :updatedAt)
                        ON CONFLICT (account_id, record_id) DO UPDATE SET
                            record_type = EXCLUDED.record_type,
                            current_entity_version = EXCLUDED.current_entity_version,
                            head_seq = EXCLUDED.head_seq,
                            head_idempotency_key = EXCLUDED.head_idempotency_key,
                            updated_at = EXCLUDED.updated_at
                        """)
                .param("accountId", head.accountId())
                .param("recordId", head.recordId())
                .param("recordType", head.recordType())
                .param("version", head.currentEntityVersion())
                .param("headSeq", head.headSequence())
                .param("idempotencyKey", head.headIdempotencyKey())
                .param("updatedAt", head.updatedAt().atOffset(ZoneOffset.UTC))
                .update();
    }

    private EntityHead map(ResultSet row, int ignored) throws SQLException {
        return new EntityHead(
                row.getObject("account_id", UUID.class),
                row.getString("record_id"),
                row.getString("record_type"),
                row.getLong("current_entity_version"),
                row.getLong("head_seq"),
                row.getString("head_idempotency_key"),
                row.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
