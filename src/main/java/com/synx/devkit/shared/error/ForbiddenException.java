package com.synx.devkit.shared.error;

public final class ForbiddenException extends DomainException {
    public ForbiddenException(String message) {
        super("forbidden", message);
    }
}
