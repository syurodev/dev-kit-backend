package com.synx.devkit.identity.adapter.out.persistence;

import com.synx.devkit.identity.application.port.out.AccountRepository;
import com.synx.devkit.identity.domain.model.Account;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** PostgreSQL adapter; the application sees only AccountRepository. */
@Repository
public class JdbcAccountRepository implements AccountRepository {
    private final JdbcClient jdbc;

    public JdbcAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Account getOrCreate(String subject, String email, String username, Instant now) {
        OffsetDateTime timestamp = now.atOffset(ZoneOffset.UTC);
        return jdbc.sql("""
                        INSERT INTO accounts(identity_subject, primary_email, username, created_at, updated_at)
                        VALUES (:subject, :email, :username, :now, :now)
                        ON CONFLICT (identity_subject) DO UPDATE SET
                            primary_email = COALESCE(EXCLUDED.primary_email, accounts.primary_email),
                            username = COALESCE(EXCLUDED.username, accounts.username),
                            updated_at = EXCLUDED.updated_at
                        RETURNING id, identity_subject, primary_email, username, created_at, updated_at
                        """)
                .param("subject", subject)
                .param("email", email)
                .param("username", username)
                .param("now", timestamp)
                .query(this::map)
                .single();
    }

    @Override
    public Optional<Account> findBySubject(String subject) {
        return jdbc.sql("""
                        SELECT id, identity_subject, primary_email, username, created_at, updated_at
                        FROM accounts WHERE identity_subject = :subject
                        """)
                .param("subject", subject)
                .query(this::map)
                .optional();
    }

    private Account map(ResultSet row, int ignored) throws SQLException {
        return new Account(
                row.getObject("id", java.util.UUID.class),
                row.getString("identity_subject"),
                row.getString("primary_email"),
                row.getString("username"),
                row.getObject("created_at", OffsetDateTime.class).toInstant(),
                row.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
