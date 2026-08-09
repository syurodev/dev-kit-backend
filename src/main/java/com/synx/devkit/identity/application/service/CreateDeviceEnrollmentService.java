package com.synx.devkit.identity.application.service;

import com.synx.devkit.audit.application.port.out.AuditEventSink;
import com.synx.devkit.audit.domain.AuditEvent;
import com.synx.devkit.identity.application.port.in.CreateDeviceEnrollmentCommand;
import com.synx.devkit.identity.application.port.in.CreateDeviceEnrollmentUseCase;
import com.synx.devkit.identity.application.port.in.DeviceEnrollmentToken;
import com.synx.devkit.identity.application.port.out.DeviceEnrollmentRepository;
import com.synx.devkit.identity.application.port.out.DeviceRepository;
import com.synx.devkit.shared.application.port.out.TransactionRunner;
import com.synx.devkit.shared.domain.SyncIdentifier;
import com.synx.devkit.shared.domain.WireLimits;
import com.synx.devkit.shared.error.ForbiddenException;
import com.synx.devkit.shared.error.ValidationException;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;

public final class CreateDeviceEnrollmentService implements CreateDeviceEnrollmentUseCase {
    private final DeviceRepository devices;
    private final DeviceEnrollmentRepository enrollments;
    private final DeviceEnrollmentTokenCodec tokens;
    private final AuditEventSink audit;
    private final TransactionRunner transactions;
    private final Clock clock;
    private final Duration lifetime;

    public CreateDeviceEnrollmentService(
            DeviceRepository devices,
            DeviceEnrollmentRepository enrollments,
            DeviceEnrollmentTokenCodec tokens,
            AuditEventSink audit,
            TransactionRunner transactions,
            Clock clock,
            Duration lifetime) {
        this.devices = devices;
        this.enrollments = enrollments;
        this.tokens = tokens;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
        this.lifetime = lifetime;
    }

    @Override
    public DeviceEnrollmentToken create(CreateDeviceEnrollmentCommand command) {
        SyncIdentifier.require("target device id", command.targetDeviceId(), WireLimits.MAX_IDENTIFIER_LENGTH);
        if (command.context().deviceId().equals(command.targetDeviceId())) {
            throw new ValidationException("target device must differ from the authorizing device");
        }
        return transactions.required(() -> {
            devices.lockRegistration(command.context().accountId());
            if (devices.find(command.context().accountId(), command.targetDeviceId()).isPresent()) {
                // Revoked IDs are deliberately not reusable and active devices
                // do not need another enrollment token.
                throw new ForbiddenException("target device is already registered");
            }
            var now = clock.instant();
            var expiresAt = now.plus(lifetime);
            String token = tokens.generate();
            enrollments.replace(
                    command.context().accountId(),
                    command.context().deviceId(),
                    command.targetDeviceId(),
                    tokens.digest(token),
                    expiresAt,
                    now);
            audit.record(new AuditEvent(
                    command.requestId(),
                    command.context().accountId(),
                    command.context().deviceId(),
                    "device.enrollment.created",
                    Map.of("target_device_id", command.targetDeviceId()),
                    now));
            return new DeviceEnrollmentToken(token, command.targetDeviceId(), expiresAt);
        });
    }
}
