package com.synx.devkit.identity.application.port.in;

public interface EstablishSyncSessionUseCase {
    SyncSession establish(EstablishSyncSessionCommand command);
}
