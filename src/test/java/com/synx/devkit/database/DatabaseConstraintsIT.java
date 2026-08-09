package com.synx.devkit.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.synx.devkit.support.PostgresTestSupport;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/** Verifies the constraints that protect identity and replication correctness. */
@SpringBootTest
@ActiveProfiles("test")
class DatabaseConstraintsIT extends PostgresTestSupport {
    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void clearData() {
        jdbc.sql("DELETE FROM audit_events").update();
        jdbc.sql("DELETE FROM device_enrollments").update();
        jdbc.sql("DELETE FROM entity_heads").update();
        jdbc.sql("DELETE FROM replication_log").update();
        jdbc.sql("DELETE FROM account_storage_usage").update();
        jdbc.sql("DELETE FROM devices").update();
        jdbc.sql("DELETE FROM accounts").update();
    }

    @Test
    void usesUuidV7AndDoesNotCreateForeignKeys() {
        UUID accountId = insertAccount("subject-a");
        Integer version = jdbc.sql("SELECT uuid_extract_version(:id)")
                .param("id", accountId)
                .query(Integer.class)
                .single();
        Integer foreignKeys = jdbc.sql("""
                        SELECT count(*) FROM information_schema.table_constraints
                        WHERE table_schema = 'public' AND constraint_type = 'FOREIGN KEY'
                        """)
                .query(Integer.class)
                .single();

        assertEquals(7, version);
        assertEquals(0, foreignKeys);
    }

    @Test
    void rejectsDuplicateIdentityAndInvalidDeviceState() {
        UUID accountId = insertAccount("subject-a");
        assertThrows(DataIntegrityViolationException.class, () -> insertAccount("subject-a"));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql("""
                        INSERT INTO devices(
                            account_id, device_id, status, protocol_version, first_seen_at, last_seen_at)
                        VALUES (:accountId, 'device-a', 'unknown', 1, :now, :now)
                        """)
                .param("accountId", accountId)
                .param("now", now)
                .update());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.sql("""
                        INSERT INTO devices(
                            account_id, device_id, status, protocol_version, first_seen_at, last_seen_at)
                        VALUES (:accountId, 'device-a', 'active', 0, :now, :now)
                        """)
                .param("accountId", accountId)
                .param("now", now)
                .update());
    }

    private UUID insertAccount(String subject) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return jdbc.sql("""
                        INSERT INTO accounts(identity_subject, created_at, updated_at)
                        VALUES (:subject, :now, :now)
                        RETURNING id
                        """)
                .param("subject", subject)
                .param("now", now)
                .query(UUID.class)
                .single();
    }
}
