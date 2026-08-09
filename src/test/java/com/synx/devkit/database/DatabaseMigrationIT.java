package com.synx.devkit.database;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.synx.devkit.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DatabaseMigrationIT extends PostgresTestSupport {
    @Autowired
    JdbcClient jdbc;

    @Test
    void createsAllPhaseATables() {
        Integer count = jdbc.sql("""
                        SELECT count(*) FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name IN (
                              'accounts', 'devices', 'device_enrollments', 'replication_log',
                              'entity_heads', 'audit_events', 'account_storage_usage')
                        """)
                .query(Integer.class)
                .single();
        assertEquals(7, count);
    }
}
