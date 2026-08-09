package com.synx.devkit.bootstrap.persistence;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Removes short-lived security metadata; replication data uses quota backpressure instead. */
@Component
@ConditionalOnProperty(
        name = "devkit.retention.enabled",
        havingValue = "true",
        matchIfMissing = true)
public final class PersistenceRetentionJob {
    private static final Logger LOG = LoggerFactory.getLogger(PersistenceRetentionJob.class);
    private final JdbcClient jdbc;
    private final Clock clock;
    private final Duration auditLifetime;

    public PersistenceRetentionJob(
            JdbcClient jdbc,
            Clock clock,
            @Value("${devkit.retention.audit-lifetime:90d}") Duration auditLifetime) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.auditLifetime = auditLifetime;
    }

    @Scheduled(cron = "${devkit.retention.cron:0 15 3 * * *}", zone = "UTC")
    public void removeExpiredMetadata() {
        var now = clock.instant().atOffset(ZoneOffset.UTC);
        int enrollments = jdbc.sql("DELETE FROM device_enrollments WHERE expires_at <= :now")
                .param("now", now)
                .update();
        int audits = jdbc.sql("DELETE FROM audit_events WHERE occurred_at < :cutoff")
                .param("cutoff", clock.instant().minus(auditLifetime).atOffset(ZoneOffset.UTC))
                .update();
        if (enrollments > 0 || audits > 0) {
            LOG.info("Retention removed expired enrollments={} auditEvents={}", enrollments, audits);
        }
    }
}
