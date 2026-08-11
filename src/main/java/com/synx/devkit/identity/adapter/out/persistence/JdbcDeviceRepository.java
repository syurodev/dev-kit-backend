package com.synx.devkit.identity.adapter.out.persistence;

import com.synx.devkit.identity.application.port.out.DeviceRepository;
import com.synx.devkit.identity.domain.model.Device;
import com.synx.devkit.identity.domain.model.DeviceStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDeviceRepository implements DeviceRepository {
    private final JdbcClient jdbc;

    public JdbcDeviceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void lockRegistration(UUID accountId) {
        // The transaction-scoped lock prevents two first-session requests from
        // both registering a different bootstrap device for the same account.
        jdbc.sql("""
                        SELECT 1
                        FROM pg_advisory_xact_lock(hashtextextended(CAST(:accountId AS text), 44127))
                        """)
                .param("accountId", accountId)
                .query(Integer.class)
                .single();
    }

    @Override
    public boolean hasAny(UUID accountId) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM devices WHERE account_id = :accountId)")
                .param("accountId", accountId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public Device register(UUID accountId, String deviceId, long protocolVersion, Instant now) {
        OffsetDateTime timestamp = now.atOffset(ZoneOffset.UTC);
        return jdbc.sql("""
                        INSERT INTO devices(account_id, device_id, status, protocol_version, first_seen_at, last_seen_at)
                        VALUES (:accountId, :deviceId, 'active', :protocol, :now, :now)
                        RETURNING id, account_id, device_id, status, protocol_version, first_seen_at, last_seen_at
                        """)
                .param("accountId", accountId)
                .param("deviceId", deviceId)
                .param("protocol", protocolVersion)
                .param("now", timestamp)
                .query(this::map)
                .single();
    }

    @Override
    public Optional<Device> touchActive(UUID accountId, String deviceId, long protocolVersion, Instant now) {
        return jdbc.sql("""
                        UPDATE devices
                        SET protocol_version = :protocol, last_seen_at = :now
                        WHERE account_id = :accountId AND device_id = :deviceId AND status = 'active'
                        RETURNING id, account_id, device_id, status, protocol_version, first_seen_at, last_seen_at
                        """)
                .param("accountId", accountId)
                .param("deviceId", deviceId)
                .param("protocol", protocolVersion)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .query(this::map)
                .optional();
    }

    @Override
    public Optional<Device> find(UUID accountId, String deviceId) {
        return jdbc.sql("""
                        SELECT id, account_id, device_id, status, protocol_version, first_seen_at, last_seen_at
                        FROM devices WHERE account_id = :accountId AND device_id = :deviceId
                        """)
                .param("accountId", accountId)
                .param("deviceId", deviceId)
                .query(this::map)
                .optional();
    }

    @Override
    public List<Device> listByAccount(UUID accountId) {
        return jdbc.sql("""
                        SELECT id, account_id, device_id, status, protocol_version, first_seen_at, last_seen_at
                        FROM devices WHERE account_id = :accountId ORDER BY first_seen_at ASC, device_id ASC
                        """)
                .param("accountId", accountId)
                .query(this::map)
                .list();
    }

    @Override
    public long countActive(UUID accountId) {
        return jdbc.sql("SELECT COUNT(*) FROM devices WHERE account_id = :accountId AND status = 'active'")
                .param("accountId", accountId)
                .query(Long.class)
                .single();
    }

    @Override
    public Optional<Device> revoke(UUID accountId, String deviceId, Instant now) {
        return jdbc.sql("""
                        UPDATE devices
                        SET status = 'revoked', last_seen_at = :now
                        WHERE account_id = :accountId AND device_id = :deviceId
                        RETURNING id, account_id, device_id, status, protocol_version, first_seen_at, last_seen_at
                        """)
                .param("accountId", accountId)
                .param("deviceId", deviceId)
                .param("now", now.atOffset(ZoneOffset.UTC))
                .query(this::map)
                .optional();
    }

    private Device map(ResultSet row, int ignored) throws SQLException {
        return new Device(
                row.getObject("id", UUID.class),
                row.getObject("account_id", UUID.class),
                row.getString("device_id"),
                DeviceStatus.valueOf(row.getString("status").toUpperCase(java.util.Locale.ROOT)),
                row.getLong("protocol_version"),
                row.getObject("first_seen_at", OffsetDateTime.class).toInstant(),
                row.getObject("last_seen_at", OffsetDateTime.class).toInstant());
    }
}
