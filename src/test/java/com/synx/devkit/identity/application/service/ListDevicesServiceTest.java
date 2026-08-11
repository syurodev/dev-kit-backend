package com.synx.devkit.identity.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.synx.devkit.identity.application.port.in.AuthorizedSyncContext;
import com.synx.devkit.identity.application.port.in.ListDevicesCommand;
import com.synx.devkit.identity.application.port.out.DeviceRepository;
import com.synx.devkit.identity.domain.model.Device;
import com.synx.devkit.identity.domain.model.DeviceStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListDevicesServiceTest {
    private static final Instant CREATED = Instant.parse("2026-08-10T10:00:00Z");
    private static final Instant LAST_SEEN = Instant.parse("2026-08-11T12:00:00Z");
    private static final UUID ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private FakeDeviceRepository devices;
    private ListDevicesService service;

    @BeforeEach
    void setUp() {
        devices = new FakeDeviceRepository();
        service = new ListDevicesService(devices);
    }

    @Test
    void listMapsDeviceFieldsAndMarksCurrentDevice() {
        devices.devices = List.of(
                device("device-a", DeviceStatus.ACTIVE),
                device("device-b", DeviceStatus.REVOKED));

        var summaries = service.list(new ListDevicesCommand(new AuthorizedSyncContext(ACCOUNT_ID, "device-a", 1L)));

        assertEquals(2, summaries.size());
        assertEquals("device-a", summaries.get(0).deviceId());
        assertEquals("active", summaries.get(0).status());
        assertEquals(CREATED, summaries.get(0).createdAt());
        assertEquals(LAST_SEEN, summaries.get(0).lastSeenAt());
        assertTrue(summaries.get(0).current());
        assertEquals("device-b", summaries.get(1).deviceId());
        assertEquals("revoked", summaries.get(1).status());
        assertFalse(summaries.get(1).current());
    }

    private static Device device(String deviceId, DeviceStatus status) {
        return new Device(UUID.randomUUID(), ACCOUNT_ID, deviceId, status, 1L, CREATED, LAST_SEEN);
    }

    private static final class FakeDeviceRepository implements DeviceRepository {
        private List<Device> devices = List.of();

        @Override
        public void lockRegistration(UUID accountId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasAny(UUID accountId) {
            throw new UnsupportedOperationException();
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
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Device> listByAccount(UUID accountId) {
            return devices;
        }

        @Override
        public long countActive(UUID accountId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Device> revoke(UUID accountId, String deviceId, Instant now) {
            throw new UnsupportedOperationException();
        }
    }
}
