package com.synx.devkit.bootstrap.persistence;

import com.synx.devkit.shared.application.port.out.TransactionRunner;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Keeps Spring transaction APIs out of application services. */
@Component
public final class SpringTransactionRunner implements TransactionRunner {
    private final TransactionTemplate transactions;

    public SpringTransactionRunner(PlatformTransactionManager transactionManager) {
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T required(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }
}
