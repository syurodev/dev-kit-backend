package com.synx.devkit.identity.application.port.in;

public interface CreateDeviceEnrollmentUseCase {
    DeviceEnrollmentToken create(CreateDeviceEnrollmentCommand command);
}
