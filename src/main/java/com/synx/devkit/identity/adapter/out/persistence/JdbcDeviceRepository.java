package com.synx.devkit.identity.adapter.out.persistence;

import com.synx.devkit.identity.application.port.out.DeviceRepository;
import com.synx.devkit.identity.domain.model.Device;
import com.synx.devkit.identity.domain.model.DeviceStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    public Device getOrCreate(UUID accountId, String deviceId, long protocolVersion, Instant now) {
        OffsetDateTime timestamp = now.atOffset(ZoneOffset.UTC);
        Optional<Device> active = jdbc.sql("""
                        INSERT INTO devices(account_id, device_id, status, protocol_version, first_seen_at, last_seen_at)
                        VALUES (:accountId, :deviceId, 'active', :protocol, :now, :now)
                        ON CONFLICT (account_id, device_id) DO UPDATE SET
                            protocol_version = EXCLUDED.protocol_version,
                            last_seen_at = EXCLUDED.last_seen_at
                        WHERE devices.status = 'active'
                        RETURNING id, account_id, device_id, status, protocol_version, first_seen_at, last_seen_at
                        """)
                .param("accountId", accountId)
                .param("deviceId", deviceId)
                .param("protocol", protocolVersion)
                .param("now", timestamp)
                .query(this::map)
                .optional();
        return active.orElseGet(() -> find(accountId, deviceId).orElseThrow());
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
