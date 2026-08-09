package com.synx.devkit.identity.application.port.out;

import com.synx.devkit.identity.domain.model.Account;
import java.time.Instant;
import java.util.Optional;

public interface AccountRepository {
    Account getOrCreate(String subject, String email, String username, Instant now);

    Optional<Account> findBySubject(String subject);
}
