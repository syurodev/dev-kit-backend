package com.synx.devkit.shared.domain;

import com.synx.devkit.shared.error.ValidationException;

/**
 * Validates identifiers embedded in replication associated data.
 *
 * <p>Path separators and control characters are rejected to mirror the Go
 * client's AAD validation rules. Keeping this rule in a framework-free class
 * avoids subtle differences between controllers and application services.</p>
 */
public final class SyncIdentifier {
    private SyncIdentifier() {
    }

    public static String require(String label, String value, int maxLength) {
        validateRequiredAndBounded(label, value, maxLength);
        if (value.indexOf('/') >= 0 || value.indexOf('\\') >= 0) {
            throw new ValidationException(label + " must not contain path separators");
        }
        return value;
    }

    /**
     * Validates an opaque protocol key that is never used as a path segment.
     *
     * <p>Replication idempotency keys are namespaced hashes. Their stable
     * namespace deliberately contains forward slashes (for example,
     * {@code dev-kit/sync/queue/v2:<hash>}), so applying path-segment rules to
     * them would reject valid clients. Length and control-character checks still
     * apply before the value reaches persistence.</p>
     */
    public static String requireOpaque(String label, String value, int maxLength) {
        validateRequiredAndBounded(label, value, maxLength);
        return value;
    }

    private static void validateRequiredAndBounded(String label, String value, int maxLength) {
        if (value == null || value.isEmpty()) {
            throw new ValidationException(label + " is required");
        }
        if (value.length() > maxLength) {
            throw new ValidationException(label + " is too long");
        }
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (Character.isISOControl(codePoint)) {
                throw new ValidationException(label + " contains control characters");
            }
            offset += Character.charCount(codePoint);
        }
    }
}
