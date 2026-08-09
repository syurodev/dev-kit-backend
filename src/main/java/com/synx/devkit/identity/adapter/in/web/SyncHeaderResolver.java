package com.synx.devkit.identity.adapter.in.web;

import com.synx.devkit.shared.domain.SyncIdentifier;
import com.synx.devkit.shared.domain.SyncProtocol;
import com.synx.devkit.shared.domain.WireLimits;
import com.synx.devkit.shared.error.ValidationException;
import org.springframework.stereotype.Component;

/** Normalizes the two headers shared by all sync endpoints. */
@Component
public final class SyncHeaderResolver {
    public static final String DEVICE_HEADER = "X-DevKit-Device-ID";
    public static final String PROTOCOL_HEADER = "X-DevKit-Sync-Protocol";

    public SyncHeaders resolve(String deviceId, String protocolText) {
        SyncIdentifier.require("device id", deviceId, WireLimits.MAX_IDENTIFIER_LENGTH);
        long protocol;
        try {
            protocol = Long.parseLong(protocolText);
        } catch (NumberFormatException error) {
            throw new ValidationException("sync protocol is invalid");
        }
        if (protocol != SyncProtocol.PROTOCOL_VERSION) {
            throw new ValidationException("unsupported sync protocol version");
        }
        return new SyncHeaders(deviceId, protocol);
    }
}
