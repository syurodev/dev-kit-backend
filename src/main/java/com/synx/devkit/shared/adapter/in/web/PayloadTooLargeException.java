package com.synx.devkit.shared.adapter.in.web;

public final class PayloadTooLargeException extends RuntimeException {
    public PayloadTooLargeException() {
        super("request body is too large");
    }
}
