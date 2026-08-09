package com.synx.devkit.identity.application.service;

import com.synx.devkit.identity.application.port.in.AuthorizeSyncRequestCommand;
import com.synx.devkit.identity.application.port.in.AuthorizeSyncRequestUseCase;
import com.synx.devkit.identity.application.port.in.AuthorizedSyncContext;
import com.synx.devkit.identity.application.port.out.AccountRepository;
import com.synx.devkit.identity.application.port.out.DeviceRepository;
import com.synx.devkit.identity.domain.model.DeviceStatus;
import com.synx.devkit.shared.error.ForbiddenException;

public final class AuthorizeSyncRequestService implements AuthorizeSyncRequestUseCase {
    private final AccountRepository accounts;
    private final DeviceRepository devices;

    public AuthorizeSyncRequestService(AccountRepository accounts, DeviceRepository devices) {
        this.accounts = accounts;
        this.devices = devices;
    }

    @Override
    public AuthorizedSyncContext authorize(AuthorizeSyncRequestCommand command) {
        IdentityValidation.validate(command.subject(), command.deviceId(), command.protocolVersion());
        var account = accounts.findBySubject(command.subject())
                .orElseThrow(() -> new ForbiddenException("sync session is not established"));
        var device = devices.find(account.id(), command.deviceId())
                .orElseThrow(() -> new ForbiddenException("sync device is not registered"));
        if (device.status() != DeviceStatus.ACTIVE) {
            throw new ForbiddenException("device is revoked");
        }
        if (device.protocolVersion() != command.protocolVersion()) {
            throw new ForbiddenException("device protocol does not match registration");
        }
        return new AuthorizedSyncContext(account.id(), device.deviceId(), device.protocolVersion());
    }
}
