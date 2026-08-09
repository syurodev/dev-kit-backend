package com.synx.devkit.shared.error;

/** Base type for errors that may be mapped to a stable public API response. */
public abstract class DomainException extends RuntimeException {
    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
