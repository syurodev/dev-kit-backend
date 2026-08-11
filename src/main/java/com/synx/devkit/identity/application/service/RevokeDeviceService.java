package com.synx.devkit.identity.application.service;

import com.synx.devkit.audit.application.port.out.AuditEventSink;
import com.synx.devkit.audit.domain.AuditEvent;
import com.synx.devkit.identity.application.port.in.RevokeDeviceCommand;
import com.synx.devkit.identity.application.port.in.RevokeDeviceUseCase;
import com.synx.devkit.identity.application.port.out.DeviceEnrollmentRepository;
import com.synx.devkit.identity.application.port.out.DeviceRepository;
import com.synx.devkit.identity.application.port.out.DeviceRevocationDenylist;
import com.synx.devkit.identity.domain.model.DeviceStatus;
import com.synx.devkit.shared.application.port.out.TransactionRunner;
import com.synx.devkit.shared.error.ConflictException;
import com.synx.devkit.shared.error.NotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public final class RevokeDeviceService implements RevokeDeviceUseCase {
    private static final Duration REVOCATION_DENYLIST_TTL = Duration.ofSeconds(60);

    private final DeviceRepository devices;
    private final DeviceEnrollmentRepository enrollments;
    private final AuditEventSink audit;
    private final TransactionRunner transactions;
    private final DeviceRevocationDenylist denylist;
    private final Clock clock;

    public RevokeDeviceService(
            DeviceRepository devices,
            DeviceEnrollmentRepository enrollments,
            AuditEventSink audit,
            TransactionRunner transactions,
            DeviceRevocationDenylist denylist,
            Clock clock) {
        this.devices = devices;
        this.enrollments = enrollments;
        this.audit = audit;
        this.transactions = transactions;
        this.denylist = denylist;
        this.clock = clock;
    }

    @Override
    public void revoke(RevokeDeviceCommand command) {
        transactions.required(() -> {
            devices.lockRegistration(command.context().accountId());
            var target = devices.find(command.context().accountId(), command.targetDeviceId())
                    .orElseThrow(() -> new NotFoundException("device was not found"));
            if (target.status() == DeviceStatus.REVOKED) {
                return null;
            }
            if (devices.countActive(command.context().accountId()) <= 1) {
                throw new ConflictException("cannot revoke the last active device");
            }
            Instant now = clock.instant();
            devices.revoke(command.context().accountId(), command.targetDeviceId(), now);
            enrollments.deleteByCreatedByDeviceId(command.context().accountId(), command.targetDeviceId());
            audit.record(new AuditEvent(
                    command.requestId(),
                    command.context().accountId(),
                    command.context().deviceId(),
                    "device.revoked",
                    Map.of(
                            "target_device_id", command.targetDeviceId(),
                            "actor_device_id", command.context().deviceId()),
                    now));
            return null;
        });
        denylist.put(command.subject(), command.targetDeviceId(), REVOCATION_DENYLIST_TTL);
    }
}
