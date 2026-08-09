package com.synx.devkit.identity.application.service;

import com.synx.devkit.shared.domain.SyncIdentifier;
import com.synx.devkit.shared.domain.SyncProtocol;
import com.synx.devkit.shared.domain.WireLimits;
import com.synx.devkit.shared.error.ValidationException;

final class IdentityValidation {
    private IdentityValidation() {
    }

    static void validate(String subject, String deviceId, long protocolVersion) {
        SyncIdentifier.require("subject", subject, WireLimits.MAX_IDENTIFIER_LENGTH);
        SyncIdentifier.require("device id", deviceId, WireLimits.MAX_IDENTIFIER_LENGTH);
        if (protocolVersion != SyncProtocol.PROTOCOL_VERSION) {
            throw new ValidationException("unsupported sync protocol version");
        }
    }
}
