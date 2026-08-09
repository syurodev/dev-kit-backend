package com.synx.devkit.replication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.synx.devkit.replication.application.service.CursorCodec;
import com.synx.devkit.shared.error.ValidationException;
import org.junit.jupiter.api.Test;

class CursorCodecTest {
    private final CursorCodec codec = new CursorCodec();

    @Test
    void emptyCursorMeansZeroAndRoundTrips() {
        assertEquals(0L, codec.decode(""));
        assertEquals(42L, codec.decode(codec.encode(42L)));
    }

    @Test
    void rejectsMalformedAndNegativeCursor() {
        assertThrows(ValidationException.class, () -> codec.decode("42"));
        assertThrows(ValidationException.class, () -> codec.decode("v1.bad"));
        assertThrows(ValidationException.class, () -> codec.decode(codec.encode(42L) + "="));
        assertThrows(ValidationException.class, () -> codec.encode(-1));
    }
}
