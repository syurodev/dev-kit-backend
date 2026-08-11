package com.synx.devkit.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.synx.devkit.identity.application.port.in.EstablishSyncSessionCommand;
import com.synx.devkit.identity.application.port.in.EstablishSyncSessionUseCase;
import com.synx.devkit.identity.application.port.out.DeviceEnrollmentRepository;
import com.synx.devkit.identity.application.port.out.DeviceRepository;
import com.synx.devkit.identity.domain.model.DeviceStatus;
import com.synx.devkit.support.PostgresTestSupport;
import java.time.Instant;
import java.util.UUID;
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
    DeviceRepository devices;
    @Autowired
    DeviceEnrollmentRepository enrollments;
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

    @Test
    void listByAccountReturnsOnlyThatAccountsDevices() {
        Instant now = Instant.now();
        UUID accountA = establishAccount("subject-a", "device-a", now);
        devices.register(accountA, "device-b", 1, now.plusSeconds(1));

        UUID accountB = establishAccount("subject-b", "device-other", now);

        var listA = devices.listByAccount(accountA);
        assertEquals(2, listA.size());
        assertTrue(listA.stream().allMatch(device -> device.accountId().equals(accountA)));

        assertEquals(1, devices.listByAccount(accountB).size());
    }

    @Test
    void revokeFlipsStatusAndDecrementsCountActive() {
        Instant now = Instant.now();
        UUID accountId = establishAccount("subject-revoke", "device-a", now);
        devices.register(accountId, "device-b", 1, now.plusSeconds(1));

        assertEquals(2, devices.countActive(accountId));

        var revoked = devices.revoke(accountId, "device-a", now.plusSeconds(60));
        assertTrue(revoked.isPresent());
        assertEquals(DeviceStatus.REVOKED, revoked.get().status());
        assertEquals(now.plusSeconds(60), revoked.get().lastSeenAt());
        assertEquals(1, devices.countActive(accountId));
    }

    @Test
    void deleteByCreatedByDeviceIdRemovesPendingEnrollmentsForCreator() {
        Instant now = Instant.now();
        UUID accountId = establishAccount("subject-enroll", "device-a", now);
        devices.register(accountId, "device-c", 1, now.plusSeconds(1));

        enrollments.replace(
                accountId, "device-a", "target-b", new byte[]{1}, now.plusSeconds(3600), now);
        enrollments.replace(
                accountId, "device-c", "target-d", new byte[]{2}, now.plusSeconds(3600), now);

        assertEquals(2, enrollmentCount(accountId));
        assertEquals(1, enrollments.deleteByCreatedByDeviceId(accountId, "device-a"));
        assertEquals(1, enrollmentCount(accountId));
    }

    private UUID establishAccount(String subject, String deviceId, Instant now) {
        return sessions.establish(new EstablishSyncSessionCommand(
                        subject,
                        null,
                        null,
                        deviceId,
                        1,
                        null,
                        now.plusSeconds(3600),
                        "persistence-test"))
                .accountId();
    }

    private int enrollmentCount(UUID accountId) {
        return jdbc.sql("SELECT count(*) FROM device_enrollments WHERE account_id = :accountId")
                .param("accountId", accountId)
                .query(Integer.class)
                .single();
    }
}
