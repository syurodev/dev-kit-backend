package com.synx.devkit.identity.adapter.out.persistence;

import com.synx.devkit.identity.application.port.out.DeviceEnrollmentRepository;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDeviceEnrollmentRepository implements DeviceEnrollmentRepository {
    private final JdbcClient jdbc;

    public JdbcDeviceEnrollmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void replace(
            UUID accountId,
            String createdByDeviceId,
            String targetDeviceId,
            byte[] tokenDigest,
            Instant expiresAt,
            Instant createdAt) {
        // Only one live token per target device is useful. Replacing it also
        // invalidates a token that may have been accidentally disclosed.
        jdbc.sql("""
                        DELETE FROM device_enrollments
                        WHERE account_id = :accountId AND target_device_id = :targetDeviceId
                        """)
                .param("accountId", accountId)
                .param("targetDeviceId", targetDeviceId)
                .update();
        jdbc.sql("""
                        INSERT INTO device_enrollments(
                            account_id, created_by_device_id, target_device_id,
                            token_digest, expires_at, created_at)
                        VALUES (
                            :accountId, :createdByDeviceId, :targetDeviceId,
                            :tokenDigest, :expiresAt, :createdAt)
                        """)
                .param("accountId", accountId)
                .param("createdByDeviceId", createdByDeviceId)
                .param("targetDeviceId", targetDeviceId)
                .param("tokenDigest", tokenDigest)
                .param("expiresAt", expiresAt.atOffset(ZoneOffset.UTC))
                .param("createdAt", createdAt.atOffset(ZoneOffset.UTC))
                .update();
    }

    @Override
    public boolean consume(
            UUID accountId,
            String targetDeviceId,
            byte[] tokenDigest,
            Instant now) {
        // DELETE ... RETURNING makes validation and consumption atomic. A
        // replay therefore fails even when two requests arrive together.
        return jdbc.sql("""
                        DELETE FROM device_enrollments
                        WHERE account_id = :accountId
                          AND target_device_id = :targetDeviceId
                          AND token_digest = :tokenDigest
                          AND expires_at > :now
                        RETURNING id
                        """)
                .param("accountId", accountId)
                .param("targetDeviceId", targetDeviceId)
                .param("tokenDigest", tokenDigest)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .query(UUID.class)
                .optional()
                .isPresent();
    }
}
