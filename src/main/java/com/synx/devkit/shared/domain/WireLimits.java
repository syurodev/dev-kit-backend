package com.synx.devkit.shared.domain;

/** Fixed limits mirrored from the production Go HTTP transport. */
public final class WireLimits {
    public static final int MAX_REQUEST_BYTES = 4 << 20;
    public static final int MAX_RESPONSE_BYTES = 4 << 20;
    public static final int MAX_CIPHERTEXT_BYTES = 1 << 20;
    public static final int MAX_OPERATIONS = 1_000;
    public static final int MAX_CURSOR_LENGTH = 512;
    public static final int MAX_IDENTIFIER_LENGTH = 256;
    public static final int MAX_RECORD_TYPE_LENGTH = 128;
    public static final int MAX_TOKEN_LENGTH = 8 << 10;

    private WireLimits() {
    }
}
