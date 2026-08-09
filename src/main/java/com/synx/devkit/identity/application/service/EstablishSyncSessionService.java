package com.synx.devkit.identity.application.service;

import com.synx.devkit.audit.application.port.out.AuditEventSink;
import com.synx.devkit.audit.domain.AuditEvent;
import com.synx.devkit.identity.application.port.in.EstablishSyncSessionCommand;
import com.synx.devkit.identity.application.port.in.EstablishSyncSessionUseCase;
import com.synx.devkit.identity.application.port.in.SyncSession;
import com.synx.devkit.identity.application.port.out.AccountRepository;
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
    private final AuditEventSink audit;
    private final TransactionRunner transactions;
    private final Clock clock;

    public EstablishSyncSessionService(
            AccountRepository accounts,
            DeviceRepository devices,
            AuditEventSink audit,
            TransactionRunner transactions,
            Clock clock) {
        this.accounts = accounts;
        this.devices = devices;
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
            var device = devices.getOrCreate(account.id(), command.deviceId(), command.protocolVersion(), now);
            if (device.status() == DeviceStatus.REVOKED) {
                throw new ForbiddenException("device is revoked");
            }
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
}
