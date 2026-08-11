package com.synx.devkit.identity.application.port.in;

import java.util.List;

public interface ListDevicesUseCase {
    List<DeviceSummary> list(ListDevicesCommand command);
}
