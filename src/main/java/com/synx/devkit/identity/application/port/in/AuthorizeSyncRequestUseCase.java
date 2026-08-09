package com.synx.devkit.identity.application.port.in;

public interface AuthorizeSyncRequestUseCase {
    AuthorizedSyncContext authorize(AuthorizeSyncRequestCommand command);
}
