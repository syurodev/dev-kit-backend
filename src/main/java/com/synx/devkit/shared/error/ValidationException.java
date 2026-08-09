package com.synx.devkit.shared.error;

public final class ValidationException extends DomainException {
    public ValidationException(String message) {
        super("validation_failed", message);
    }
}
