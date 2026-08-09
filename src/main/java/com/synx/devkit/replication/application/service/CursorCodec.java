package com.synx.devkit.replication.application.service;

import com.synx.devkit.shared.domain.WireLimits;
import com.synx.devkit.shared.error.ValidationException;
import java.nio.ByteBuffer;
import java.util.Base64;

/** Versioned opaque cursor. Empty input means sequence zero. */
public final class CursorCodec {
    private static final String PREFIX = "v1.";

    public long decode(String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return 0L;
        }
        if (cursor.length() > WireLimits.MAX_CURSOR_LENGTH || !cursor.startsWith(PREFIX)) {
            throw new ValidationException("cursor is invalid");
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(cursor.substring(PREFIX.length()));
            if (bytes.length != Long.BYTES) {
                throw new ValidationException("cursor is invalid");
            }
            long sequence = ByteBuffer.wrap(bytes).getLong();
            if (sequence < 0) {
                throw new ValidationException("cursor is invalid");
            }
            // Only accept the canonical unpadded representation. This avoids
            // multiple textual cursors referring to the same sequence.
            if (!encode(sequence).equals(cursor)) {
                throw new ValidationException("cursor is invalid");
            }
            return sequence;
        } catch (IllegalArgumentException error) {
            throw new ValidationException("cursor is invalid");
        }
    }

    public String encode(long sequence) {
        if (sequence < 0) {
            throw new ValidationException("cursor sequence is invalid");
        }
        byte[] bytes = ByteBuffer.allocate(Long.BYTES).putLong(sequence).array();
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
