package com.synx.devkit.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.synx.devkit.identity.application.port.in.EstablishSyncSessionCommand;
import com.synx.devkit.identity.application.port.in.EstablishSyncSessionUseCase;
import com.synx.devkit.support.PostgresTestSupport;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class IdentityPersistenceIT extends PostgresTestSupport {
    @Autowired
    EstablishSyncSessionUseCase sessions;
    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void clearIdentityData() {
        jdbc.sql("DELETE FROM audit_events").update();
        jdbc.sql("DELETE FROM device_enrollments").update();
        jdbc.sql("DELETE FROM account_storage_usage").update();
        jdbc.sql("DELETE FROM devices").update();
        jdbc.sql("DELETE FROM accounts").update();
    }

    @Test
    void concurrentFirstSessionConvergesOnOneAccountAndDevice() throws Exception {
        Callable<java.util.UUID> establish = () -> sessions.establish(new EstablishSyncSessionCommand(
                        "concurrent-subject",
                        null,
                        null,
                        "concurrent-device",
                        1,
                        null,
                        Instant.now().plusSeconds(600),
                        "concurrent-test"))
                .accountId();

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(establish);
            var second = executor.submit(establish);
            assertEquals(first.get(), second.get());
        }

        assertEquals(1, jdbc.sql("SELECT count(*) FROM accounts").query(Integer.class).single());
        assertEquals(1, jdbc.sql("SELECT count(*) FROM devices").query(Integer.class).single());
    }
}
