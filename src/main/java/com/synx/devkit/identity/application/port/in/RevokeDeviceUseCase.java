package com.synx.devkit.identity.application.port.in;

public interface RevokeDeviceUseCase {
    void revoke(RevokeDeviceCommand command);
}
