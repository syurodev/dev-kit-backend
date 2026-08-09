package com.synx.devkit.replication.adapter.out.persistence;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.synx.devkit.replication.application.port.out.ReplicationLogRepository;
import com.synx.devkit.replication.domain.model.ContentDigest;
import com.synx.devkit.replication.domain.model.OperationType;
import com.synx.devkit.replication.domain.model.ReplicationEnvelope;
import com.synx.devkit.replication.domain.model.ReplicationOperation;
import com.synx.devkit.replication.domain.model.StoredOperation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Explicit SQL keeps append ordering, JSONB and cursor queries reviewable. */
@Repository
public class JdbcReplicationLogRepository implements ReplicationLogRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public JdbcReplicationLogRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<StoredOperation> findByOperationId(UUID accountId, String operationId) {
        return jdbc.sql(selectBase() + " WHERE account_id = :accountId AND operation_id = :operationId")
                .param("accountId", accountId)
                .param("operationId", operationId)
                .query(this::map)
                .optional();
    }

    @Override
    public Optional<StoredOperation> findByIdempotencyKey(UUID accountId, String idempotencyKey) {
        return jdbc.sql(selectBase() + " WHERE account_id = :accountId AND idempotency_key = :idempotencyKey")
                .param("accountId", accountId)
                .param("idempotencyKey", idempotencyKey)
                .query(this::map)
                .optional();
    }

    @Override
    public long append(
            UUID accountId,
            ReplicationOperation operation,
            ContentDigest digest,
            Instant createdAt) {
        var envelope = operation.envelope();
        return jdbc.sql("""
                        INSERT INTO replication_log(
                            account_id, record_id, record_type, device_id,
                            entity_version, operation, idempotency_key, operation_id,
                            envelope, content_digest, created_at)
                        VALUES (
                            :accountId, :recordId, :recordType, :deviceId,
                            :entityVersion, :operation, :idempotencyKey, :operationId,
                            CAST(:envelope AS jsonb), :digest, :createdAt)
                        RETURNING seq
                        """)
                .param("accountId", accountId)
                .param("recordId", envelope.recordId())
                .param("recordType", envelope.recordType())
                .param("deviceId", envelope.deviceId())
                .param("entityVersion", envelope.entityVersion())
                .param("operation", operation.operation().wireValue())
                .param("idempotencyKey", operation.idempotencyKey())
                .param("operationId", envelope.operationId())
                .param("envelope", writeEnvelope(envelope))
                .param("digest", digest.bytes())
                .param("createdAt", createdAt.atOffset(ZoneOffset.UTC))
                .query(Long.class)
                .single();
    }

    @Override
    public List<StoredOperation> pageAfter(
            UUID accountId,
            long sequence,
            int limit,
            int responseBudgetBytes) {
        return jdbc.sql("""
                        WITH limited AS (
                            SELECT seq, account_id, record_id, record_type, device_id,
                                   entity_version, operation, idempotency_key, operation_id,
                                   envelope, content_digest, created_at
                            FROM replication_log
                            WHERE account_id = :accountId AND seq > :sequence
                            ORDER BY seq ASC
                            LIMIT :limit
                        ), sized AS (
                            SELECT limited.*,
                                   -- Conservative allowance for the outer
                                   -- operation JSON and maximum-length routing
                                   -- identifiers not stored inside envelope.
                                   SUM(octet_length(envelope::text) + 2048)
                                       OVER (ORDER BY seq ASC) AS cumulative_bytes
                            FROM limited
                        )
                        SELECT seq, account_id, record_id, record_type, device_id,
                               entity_version, operation, idempotency_key, operation_id,
                               envelope::text AS envelope_json, content_digest, created_at
                        FROM sized
                        WHERE cumulative_bytes <= :budget
                        ORDER BY seq ASC
                        """)
                .param("accountId", accountId)
                .param("sequence", sequence)
                .param("limit", limit)
                .param("budget", responseBudgetBytes)
                .query(this::map)
                .list();
    }

    private String selectBase() {
        return """
                SELECT seq, account_id, record_id, record_type, device_id,
                       entity_version, operation, idempotency_key, operation_id,
                       envelope::text AS envelope_json, content_digest, created_at
                FROM replication_log
                """;
    }

    private StoredOperation map(ResultSet row, int ignored) throws SQLException {
        ReplicationEnvelope envelope = readEnvelope(row.getString("envelope_json"));
        ReplicationOperation operation = new ReplicationOperation(
                row.getString("idempotency_key"),
                OperationType.fromWire(row.getString("operation")),
                envelope);
        return new StoredOperation(
                row.getLong("seq"),
                row.getObject("account_id", UUID.class),
                operation,
                new ContentDigest(row.getBytes("content_digest")),
                row.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private String writeEnvelope(ReplicationEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(EnvelopeJson.from(envelope));
        } catch (JacksonException error) {
            throw new IllegalStateException("cannot serialize replication envelope", error);
        }
    }

    private ReplicationEnvelope readEnvelope(String json) {
        try {
            return objectMapper.readValue(json, EnvelopeJson.class).toDomain();
        } catch (JacksonException error) {
            throw new IllegalStateException("stored replication envelope is invalid", error);
        }
    }

    private record EnvelopeJson(
            @JsonProperty("record_id") String recordId,
            @JsonProperty("record_type") String recordType,
            @JsonProperty("key_version") long keyVersion,
            @JsonProperty("envelope_version") long envelopeVersion,
            @JsonProperty("account_id") String accountId,
            @JsonProperty("device_id") String deviceId,
            @JsonProperty("protocol_version") long protocolVersion,
            @JsonProperty("operation_id") String operationId,
            @JsonProperty("entity_version") long entityVersion,
            String operation,
            byte[] ciphertext) {

        static EnvelopeJson from(ReplicationEnvelope envelope) {
            return new EnvelopeJson(
                    envelope.recordId(),
                    envelope.recordType(),
                    envelope.keyVersion(),
                    envelope.envelopeVersion(),
                    envelope.accountId(),
                    envelope.deviceId(),
                    envelope.protocolVersion(),
                    envelope.operationId(),
                    envelope.entityVersion(),
                    envelope.operation().wireValue(),
                    envelope.ciphertext());
        }

        ReplicationEnvelope toDomain() {
            return new ReplicationEnvelope(
                    recordId,
                    recordType,
                    keyVersion,
                    envelopeVersion,
                    accountId,
                    deviceId,
                    protocolVersion,
                    operationId,
                    entityVersion,
                    OperationType.fromWire(operation),
                    ciphertext);
        }
    }
}
