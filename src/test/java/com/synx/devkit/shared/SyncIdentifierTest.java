package com.synx.devkit.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.synx.devkit.shared.domain.SyncIdentifier;
import com.synx.devkit.shared.error.ValidationException;
import org.junit.jupiter.api.Test;

class SyncIdentifierTest {
    @Test
    void acceptsSafeIdentifierAtLimit() {
        String value = "a".repeat(16);
        assertEquals(value, SyncIdentifier.require("id", value, 16));
    }

    @Test
    void rejectsEmptyControlAndPathSeparator() {
        assertThrows(ValidationException.class, () -> SyncIdentifier.require("id", "", 16));
        assertThrows(ValidationException.class, () -> SyncIdentifier.require("id", "a\nb", 16));
        assertThrows(ValidationException.class, () -> SyncIdentifier.require("id", "a/b", 16));
        assertThrows(ValidationException.class, () -> SyncIdentifier.require("id", "a\\b", 16));
    }
}
