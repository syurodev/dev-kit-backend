package com.synx.devkit.identity.application.service;

import com.synx.devkit.identity.application.port.in.DeviceSummary;
import com.synx.devkit.identity.application.port.in.ListDevicesCommand;
import com.synx.devkit.identity.application.port.in.ListDevicesUseCase;
import com.synx.devkit.identity.application.port.out.DeviceRepository;
import java.util.List;
import java.util.Locale;

public final class ListDevicesService implements ListDevicesUseCase {
    private final DeviceRepository devices;

    public ListDevicesService(DeviceRepository devices) {
        this.devices = devices;
    }

    @Override
    public List<DeviceSummary> list(ListDevicesCommand command) {
        return devices.listByAccount(command.context().accountId()).stream()
                .map(device -> new DeviceSummary(
                        device.deviceId(),
                        device.status().name().toLowerCase(Locale.ROOT),
                        device.firstSeenAt(),
                        device.lastSeenAt(),
                        device.deviceId().equals(command.context().deviceId())))
                .toList();
    }
}
