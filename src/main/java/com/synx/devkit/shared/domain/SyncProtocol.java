package com.synx.devkit.shared.domain;

/**
 * Wire versions shared by every inbound adapter and replication use case.
 *
 * <p>These values are intentionally constants rather than runtime properties:
 * changing either value requires a versioned protocol decision on both the Go
 * client and the backend.</p>
 */
public final class SyncProtocol {
    public static final long PROTOCOL_VERSION = 1L;
    public static final long ENVELOPE_VERSION = 2L;
    public static final long MAX_UNSIGNED_INT = 0xffff_ffffL;

    private SyncProtocol() {
    }
}
