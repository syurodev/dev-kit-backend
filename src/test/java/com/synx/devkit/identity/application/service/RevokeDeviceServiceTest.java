package com.synx.devkit.identity.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.synx.devkit.audit.application.port.out.AuditEventSink;
import com.synx.devkit.audit.domain.AuditEvent;
import com.synx.devkit.identity.application.port.in.AuthorizedSyncContext;
import com.synx.devkit.identity.application.port.in.ListDevicesCommand;
import com.synx.devkit.identity.application.port.in.RevokeDeviceCommand;
import com.synx.devkit.identity.application.port.out.DeviceEnrollmentRepository;
import com.synx.devkit.identity.application.port.out.DeviceRepository;
import com.synx.devkit.identity.application.port.out.DeviceRevocationDenylist;
import com.synx.devkit.identity.domain.model.Device;
import com.synx.devkit.identity.domain.model.DeviceStatus;
import com.synx.devkit.shared.application.port.out.TransactionRunner;
import com.synx.devkit.shared.error.ConflictException;
import com.synx.devkit.shared.error.NotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RevokeDeviceServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-11T12:00:00Z");
    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private FakeDeviceRepository devices;
    private FakeDeviceEnrollmentRepository enrollments;
    private FakeAuditEventSink audit;
    private FakeDeviceRevocationDenylist denylist;
    private RevokeDeviceService service;

    @BeforeEach
    void setUp() {
        devices = new FakeDeviceRepository();
        enrollments = new FakeDeviceEnrollmentRepository();
        audit = new FakeAuditEventSink();
        denylist = new FakeDeviceRevocationDenylist();
        service = new RevokeDeviceService(
                devices,
                enrollments,
                audit,
                new TransactionRunner() {
                    @Override
                    public <T> T required(java.util.function.Supplier<T> work) {
                        return work.get();
                    }
                },
                denylist,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void revokeRejectsLastActiveDevice() {
        devices.addDevice(activeDevice("device-a"));
        var command = command("device-a");

        assertThrows(ConflictException.class, () -> service.revoke(command));
        assertTrue(denylist.puts.isEmpty());
    }

    @Test
    void revokeSucceedsWhenAnotherActiveDeviceExists() {
        devices.addDevice(activeDevice("device-a"));
        devices.addDevice(activeDevice("device-b"));
        var command = command("device-b");

        service.revoke(command);

        assertEquals(DeviceStatus.REVOKED, devices.find(ACCOUNT_ID, "device-b").orElseThrow().status());
        assertEquals(1, enrollments.deletedByCreator.size());
        assertEquals("device-b", enrollments.deletedByCreator.getFirst());
        assertEquals(1, audit.events.size());
        assertEquals("device.revoked", audit.events.getFirst().eventType());
        assertEquals(1, denylist.puts.size());
        assertEquals("subject-1", denylist.puts.getFirst().subject());
        assertEquals("device-b", denylist.puts.getFirst().deviceId());
        assertEquals(Duration.ofSeconds(60), denylist.puts.getFirst().ttl());
    }

    @Test
    void revokeIsIdempotentForAlreadyRevokedDevice() {
        devices.addDevice(revokedDevice("device-a"));
        devices.addDevice(activeDevice("device-b"));
        var command = command("device-a");

        service.revoke(command);

        assertTrue(audit.events.isEmpty());
        assertTrue(enrollments.deletedByCreator.isEmpty());
        assertEquals(1, denylist.puts.size());
        assertEquals("subject-1", denylist.puts.getFirst().subject());
        assertEquals("device-a", denylist.puts.getFirst().deviceId());
        assertEquals(Duration.ofSeconds(60), denylist.puts.getFirst().ttl());
    }

    @Test
    void revokeThrowsNotFoundWhenDeviceMissing() {
        devices.addDevice(activeDevice("device-a"));
        devices.addDevice(activeDevice("device-b"));
        var command = command("missing");

        assertThrows(NotFoundException.class, () -> service.revoke(command));
        assertTrue(denylist.puts.isEmpty());
    }

    private static RevokeDeviceCommand command(String targetDeviceId) {
        return new RevokeDeviceCommand(
                new AuthorizedSyncContext(ACCOUNT_ID, "device-a", 1L),
                "subject-1",
                targetDeviceId,
                "request-1");
    }

    private static Device activeDevice(String deviceId) {
        return device(deviceId, DeviceStatus.ACTIVE);
    }

    private static Device revokedDevice(String deviceId) {
        return device(deviceId, DeviceStatus.REVOKED);
    }

    private static Device device(String deviceId, DeviceStatus status) {
        return new Device(
                UUID.randomUUID(),
                ACCOUNT_ID,
                deviceId,
                status,
                1L,
                NOW.minusSeconds(3600),
                NOW.minusSeconds(60));
    }

    private static final class FakeDeviceRepository implements DeviceRepository {
        private final List<Device> devices = new ArrayList<>();

        void addDevice(Device device) {
            devices.add(device);
        }

        @Override
        public void lockRegistration(UUID accountId) {}

        @Override
        public boolean hasAny(UUID accountId) {
            return !devices.isEmpty();
        }

        @Override
        public Device register(UUID accountId, String deviceId, long protocolVersion, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Device> touchActive(
                UUID accountId, String deviceId, long protocolVersion, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Device> find(UUID accountId, String deviceId) {
            return devices.stream()
                    .filter(device -> device.accountId().equals(accountId) && device.deviceId().equals(deviceId))
                    .findFirst();
        }

        @Override
        public List<Device> listByAccount(UUID accountId) {
            return devices.stream().filter(device -> device.accountId().equals(accountId)).toList();
        }

        @Override
        public long countActive(UUID accountId) {
            return devices.stream()
                    .filter(device -> device.accountId().equals(accountId) && device.status() == DeviceStatus.ACTIVE)
                    .count();
        }

        @Override
        public Optional<Device> revoke(UUID accountId, String deviceId, Instant now) {
            for (int i = 0; i < devices.size(); i++) {
                var device = devices.get(i);
                if (device.accountId().equals(accountId) && device.deviceId().equals(deviceId)) {
                    var revoked = new Device(
                            device.id(),
                            device.accountId(),
                            device.deviceId(),
                            DeviceStatus.REVOKED,
                            device.protocolVersion(),
                            device.firstSeenAt(),
                            now);
                    devices.set(i, revoked);
                    return Optional.of(revoked);
                }
            }
            return Optional.empty();
        }
    }

    private static final class FakeDeviceEnrollmentRepository implements DeviceEnrollmentRepository {
        private final List<String> deletedByCreator = new ArrayList<>();

        @Override
        public void replace(
                UUID accountId,
                String createdByDeviceId,
                String targetDeviceId,
                byte[] tokenDigest,
                Instant expiresAt,
                Instant createdAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean consume(UUID accountId, String targetDeviceId, byte[] tokenDigest, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int deleteByCreatedByDeviceId(UUID accountId, String createdByDeviceId) {
            deletedByCreator.add(createdByDeviceId);
            return 1;
        }
    }

    private static final class FakeAuditEventSink implements AuditEventSink {
        private final List<AuditEvent> events = new ArrayList<>();

        @Override
        public void record(AuditEvent event) {
            events.add(event);
        }
    }

    private record DenylistPut(String subject, String deviceId, Duration ttl) {}

    private static final class FakeDeviceRevocationDenylist implements DeviceRevocationDenylist {
        private final List<DenylistPut> puts = new ArrayList<>();

        @Override
        public void put(String subject, String deviceId, Duration ttl) {
            puts.add(new DenylistPut(subject, deviceId, ttl));
        }
    }
}
