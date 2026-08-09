package com.synx.devkit.shared.application.port.out;

import java.util.function.Supplier;

/** Framework-neutral transaction boundary used by application services. */
public interface TransactionRunner {
    <T> T required(Supplier<T> work);
}
