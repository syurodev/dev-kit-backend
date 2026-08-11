package com.synx.devkit.shared.error;

public final class NotFoundException extends DomainException {
    public NotFoundException(String message) {
        super("not_found", message);
    }
}
