package com.synx.devkit.shared.error;

public final class QuotaExceededException extends DomainException {
    public QuotaExceededException() {
        super("quota_exceeded", "account storage quota is exhausted");
    }
}
