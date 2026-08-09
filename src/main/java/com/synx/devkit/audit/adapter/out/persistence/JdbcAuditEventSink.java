package com.synx.devkit.audit.adapter.out.persistence;

import com.synx.devkit.audit.application.port.out.AuditEventSink;
import com.synx.devkit.audit.domain.AuditEvent;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class JdbcAuditEventSink implements AuditEventSink {
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public JdbcAuditEventSink(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void record(AuditEvent event) {
        jdbc.sql("""
                        INSERT INTO audit_events(
                            request_id, account_id, device_id, event_type, detail, occurred_at)
                        VALUES (
                            :requestId, :accountId, :deviceId, :eventType,
                            CAST(:detail AS jsonb), :occurredAt)
                        """)
                .param("requestId", event.requestId())
                .param("accountId", event.accountId())
                .param("deviceId", event.deviceId())
                .param("eventType", event.eventType())
                .param("detail", writeDetail(event))
                .param("occurredAt", event.occurredAt().atOffset(ZoneOffset.UTC))
                .update();
    }

    private String writeDetail(AuditEvent event) {
        try {
            return objectMapper.writeValueAsString(event.detail());
        } catch (JacksonException error) {
            throw new IllegalStateException("cannot serialize safe audit metadata", error);
        }
    }
}
