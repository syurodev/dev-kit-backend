package com.synx.devkit.shared.error;

/** Signals a persistence identity collision that cannot be treated as replay. */
public final class ConflictException extends DomainException {
    public ConflictException(String message) {
        super("conflict", message);
    }
}
