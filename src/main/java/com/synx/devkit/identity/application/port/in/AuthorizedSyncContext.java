package com.synx.devkit.identity.application.port.in;

import java.util.UUID;

/** Account/device identity re-checked for each push and pull request. */
public record AuthorizedSyncContext(UUID accountId, String deviceId, long protocolVersion) {
}
