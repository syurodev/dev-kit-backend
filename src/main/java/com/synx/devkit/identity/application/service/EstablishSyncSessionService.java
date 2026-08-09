package com.synx.devkit.identity.application.service;

import com.synx.devkit.audit.application.port.out.AuditEventSink;
import com.synx.devkit.audit.domain.AuditEvent;
import com.synx.devkit.identity.application.port.in.EstablishSyncSessionCommand;
import com.synx.devkit.identity.application.port.in.EstablishSyncSessionUseCase;
import com.synx.devkit.identity.application.port.in.SyncSession;
import com.synx.devkit.identity.application.port.out.AccountRepository;
import com.synx.devkit.identity.application.port.out.DeviceEnrollmentRepository;
import com.synx.devkit.identity.application.port.out.DeviceRepository;
import com.synx.devkit.identity.domain.model.DeviceStatus;
import com.synx.devkit.shared.application.port.out.TransactionRunner;
import com.synx.devkit.shared.error.ForbiddenException;
import com.synx.devkit.shared.error.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

public final class EstablishSyncSessionService implements EstablishSyncSessionUseCase {
    private final AccountRepository accounts;
    private final DeviceRepository devices;
    private final DeviceEnrollmentRepository enrollments;
    private final DeviceEnrollmentTokenCodec tokens;
    private final AuditEventSink audit;
    private final TransactionRunner transactions;
    private final Clock clock;

    public EstablishSyncSessionService(
            AccountRepository accounts,
            DeviceRepository devices,
            DeviceEnrollmentRepository enrollments,
            DeviceEnrollmentTokenCodec tokens,
            AuditEventSink audit,
            TransactionRunner transactions,
            Clock clock) {
        this.accounts = accounts;
        this.devices = devices;
        this.enrollments = enrollments;
        this.tokens = tokens;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public SyncSession establish(EstablishSyncSessionCommand command) {
        IdentityValidation.validate(command.subject(), command.deviceId(), command.protocolVersion());
        if (command.upstreamExpiresAt() == null || !clock.instant().isBefore(command.upstreamExpiresAt())) {
            throw new ValidationException("upstream authentication has expired");
        }
        return transactions.required(() -> {
            Instant now = clock.instant();
            var account = accounts.getOrCreate(command.subject(), command.email(), command.username(), now);
            devices.lockRegistration(account.id());
            var existing = devices.find(account.id(), command.deviceId());
            var device = existing.isPresent()
                    ? establishExisting(account.id(), command, existing.get().status(), now)
                    : registerNew(account.id(), command, now);
            audit.record(new AuditEvent(
                    command.requestId(),
                    account.id(),
                    device.deviceId(),
                    "session.accepted",
                    Map.of("protocol_version", device.protocolVersion()),
                    now));
            return new SyncSession(account.id(), device.deviceId(), command.upstreamExpiresAt());
        });
    }

    private com.synx.devkit.identity.domain.model.Device establishExisting(
            java.util.UUID accountId,
            EstablishSyncSessionCommand command,
            DeviceStatus status,
            Instant now) {
        if (status == DeviceStatus.REVOKED) {
            throw new ForbiddenException("device is revoked");
        }
        return devices.touchActive(accountId, command.deviceId(), command.protocolVersion(), now)
                .orElseThrow(() -> new ForbiddenException("device is revoked"));
    }

    private com.synx.devkit.identity.domain.model.Device registerNew(
            java.util.UUID accountId,
            EstablishSyncSessionCommand command,
            Instant now) {
        if (devices.hasAny(accountId)) {
            if (command.enrollmentToken() == null || command.enrollmentToken().isBlank()) {
                throw new ForbiddenException("valid device enrollment is required");
            }
            byte[] digest;
            try {
                digest = tokens.digest(command.enrollmentToken());
            } catch (ValidationException error) {
                // Enrollment failures intentionally share one public outcome;
                // callers cannot use validation differences as a token oracle.
                throw new ForbiddenException("valid device enrollment is required");
            }
            boolean accepted = enrollments.consume(accountId, command.deviceId(), digest, now);
            if (!accepted) {
                throw new ForbiddenException("valid device enrollment is required");
            }
        }
        // Only the first device may bootstrap without an enrollment secret.
        return devices.register(accountId, command.deviceId(), command.protocolVersion(), now);
    }
}
