package com.synx.devkit.identity.application.port.out;

import com.synx.devkit.identity.domain.model.Device;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface DeviceRepository {
    /** Serializes registration decisions for one account inside the current transaction. */
    void lockRegistration(UUID accountId);

    boolean hasAny(UUID accountId);

    Device register(UUID accountId, String deviceId, long protocolVersion, Instant now);

    Optional<Device> touchActive(UUID accountId, String deviceId, long protocolVersion, Instant now);

    Optional<Device> find(UUID accountId, String deviceId);
}
