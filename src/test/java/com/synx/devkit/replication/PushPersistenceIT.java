package com.synx.devkit.replication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.synx.devkit.identity.application.port.in.AuthorizeSyncRequestCommand;
import com.synx.devkit.identity.application.port.in.AuthorizeSyncRequestUseCase;
import com.synx.devkit.identity.application.port.in.AuthorizedSyncContext;
import com.synx.devkit.identity.application.port.in.CreateDeviceEnrollmentCommand;
import com.synx.devkit.identity.application.port.in.CreateDeviceEnrollmentUseCase;
import com.synx.devkit.identity.application.port.in.EstablishSyncSessionCommand;
import com.synx.devkit.identity.application.port.in.EstablishSyncSessionUseCase;
import com.synx.devkit.replication.application.port.in.PushReplicationCommand;
import com.synx.devkit.replication.application.port.in.PushReplicationResult;
import com.synx.devkit.replication.application.port.in.PushReplicationUseCase;
import com.synx.devkit.replication.domain.model.OperationType;
import com.synx.devkit.replication.domain.model.ReplicationEnvelope;
import com.synx.devkit.replication.domain.model.ReplicationOperation;
import com.synx.devkit.shared.error.ValidationException;
import com.synx.devkit.shared.error.QuotaExceededException;
import com.synx.devkit.support.PostgresTestSupport;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PushPersistenceIT extends PostgresTestSupport {
    @Autowired
    EstablishSyncSessionUseCase sessions;
    @Autowired
    AuthorizeSyncRequestUseCase authorization;
    @Autowired
    CreateDeviceEnrollmentUseCase enrollments;
    @Autowired
    PushReplicationUseCase push;
    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void clearBusinessData() {
        jdbc.sql("DELETE FROM audit_events").update();
        jdbc.sql("DELETE FROM device_enrollments").update();
        jdbc.sql("DELETE FROM entity_heads").update();
        jdbc.sql("DELETE FROM replication_log").update();
        jdbc.sql("DELETE FROM account_storage_usage").update();
        jdbc.sql("DELETE FROM devices").update();
        jdbc.sql("DELETE FROM accounts").update();
    }

    @Test
    void exactReplayAcknowledgesWithoutAppendingAgain() {
        AuthorizedSyncContext context = context("device-a");
        var operation = operation(context, "record-1", "idem-1", "op-1", 1);

        assertEquals(List.of("idem-1"), push.push(command(context, List.of(operation))).sentIdempotencyKeys());
        assertEquals(List.of("idem-1"), push.push(command(context, List.of(operation))).sentIdempotencyKeys());
        assertEquals(1, count("replication_log"));
        assertEquals(1, count("entity_heads"));
    }

    @Test
    void versionGapRollsBackWholeBatch() {
        AuthorizedSyncContext context = context("device-a");
        var first = operation(context, "record-1", "idem-1", "op-1", 1);
        var gap = operation(context, "record-1", "idem-3", "op-3", 3);

        assertThrows(ValidationException.class, () -> push.push(command(context, List.of(first, gap))));
        assertEquals(0, count("replication_log"));
        assertEquals(0, count("entity_heads"));
        assertEquals(0, countEvent("push.completed"));
    }

    @Test
    void exhaustedAccountQuotaRejectsAndRollsBackAppend() {
        AuthorizedSyncContext context = context("device-a");
        jdbc.sql("""
                        INSERT INTO account_storage_usage(
                            account_id, operations_used, bytes_used, updated_at)
                        VALUES (:accountId, 1000000, 0, CURRENT_TIMESTAMP)
                        """)
                .param("accountId", context.accountId())
                .update();

        var operation = operation(context, "record-1", "idem-1", "op-1", 1);
        assertThrows(QuotaExceededException.class,
                () -> push.push(command(context, List.of(operation))));
        assertEquals(0, count("replication_log"));
        assertEquals(0, count("entity_heads"));
    }

    @Test
    void concurrentSameVersionProducesOneAcceptAndOneConflict() throws Exception {
        AuthorizedSyncContext firstContext = context("device-a");
        AuthorizedSyncContext secondContext = enrolledContext(firstContext, "device-b");
        var first = command(firstContext, List.of(operation(
                firstContext, "record-1", "idem-a", "op-a", 1)));
        var second = command(secondContext, List.of(operation(
                secondContext, "record-1", "idem-b", "op-b", 1)));

        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futureA = executor.submit(() -> {
                start.await();
                return push.push(first);
            });
            var futureB = executor.submit(() -> {
                start.await();
                return push.push(second);
            });
            start.countDown();
            PushReplicationResult resultA = futureA.get();
            PushReplicationResult resultB = futureB.get();
            int sent = resultA.sentIdempotencyKeys().size() + resultB.sentIdempotencyKeys().size();
            int conflicts = resultA.conflicts().size() + resultB.conflicts().size();
            assertEquals(1, sent);
            assertEquals(1, conflicts);
        }
        assertEquals(1, count("replication_log"));
        assertEquals(1, count("entity_heads"));
    }

    private AuthorizedSyncContext context(String deviceId) {
        sessions.establish(new EstablishSyncSessionCommand(
                "subject-1", null, null, deviceId, 1, null,
                Instant.now().plusSeconds(3600), "session-test"));
        return authorization.authorize(new AuthorizeSyncRequestCommand("subject-1", deviceId, 1));
    }

    private AuthorizedSyncContext enrolledContext(AuthorizedSyncContext authorizing, String deviceId) {
        var token = enrollments.create(new CreateDeviceEnrollmentCommand(
                authorizing, deviceId, "enrollment-test"));
        sessions.establish(new EstablishSyncSessionCommand(
                "subject-1", null, null, deviceId, 1, token.token(),
                Instant.now().plusSeconds(3600), "session-test"));
        return authorization.authorize(new AuthorizeSyncRequestCommand("subject-1", deviceId, 1));
    }

    private PushReplicationCommand command(
            AuthorizedSyncContext context,
            List<ReplicationOperation> operations) {
        return new PushReplicationCommand(context, operations, "test-request");
    }

    private ReplicationOperation operation(
            AuthorizedSyncContext context,
            String recordId,
            String idempotencyKey,
            String operationId,
            long version) {
        return new ReplicationOperation(
                idempotencyKey,
                OperationType.UPDATE,
                new ReplicationEnvelope(
                        recordId,
                        "note",
                        1,
                        2,
                        context.accountId().toString(),
                        context.deviceId(),
                        1,
                        operationId,
                        version,
                        OperationType.UPDATE,
                        new byte[]{1, 2, 3, 4}));
    }

    private int count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
    }

    private int countEvent(String eventType) {
        return jdbc.sql("SELECT count(*) FROM audit_events WHERE event_type = :eventType")
                .param("eventType", eventType)
                .query(Integer.class)
                .single();
    }
}
