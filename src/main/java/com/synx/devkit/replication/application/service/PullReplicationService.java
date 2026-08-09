package com.synx.devkit.replication.application.service;

import com.synx.devkit.audit.application.port.out.AuditEventSink;
import com.synx.devkit.audit.domain.AuditEvent;
import com.synx.devkit.replication.application.port.in.PullReplicationQuery;
import com.synx.devkit.replication.application.port.in.PullReplicationResult;
import com.synx.devkit.replication.application.port.in.PullReplicationUseCase;
import com.synx.devkit.replication.application.port.out.ReplicationLogRepository;
import com.synx.devkit.shared.domain.WireLimits;
import com.synx.devkit.shared.error.ValidationException;
import java.time.Clock;
import java.util.Map;

public final class PullReplicationService implements PullReplicationUseCase {
    private final CursorCodec cursors;
    private final ReplicationLogRepository log;
    private final AuditEventSink audit;
    private final Clock clock;

    public PullReplicationService(
            CursorCodec cursors,
            ReplicationLogRepository log,
            AuditEventSink audit,
            Clock clock) {
        this.cursors = cursors;
        this.log = log;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public PullReplicationResult pull(PullReplicationQuery query) {
        if (query.limit() <= 0 || query.limit() > WireLimits.MAX_OPERATIONS) {
            throw new ValidationException("pull limit is invalid");
        }
        long after = cursors.decode(query.cursor());
        var page = log.pageAfter(
                query.context().accountId(),
                after,
                query.limit(),
                WireLimits.MAX_RESPONSE_BYTES - 2048);
        long next = page.isEmpty() ? after : page.getLast().sequence();
        audit.record(new AuditEvent(
                query.requestId(),
                query.context().accountId(),
                query.context().deviceId(),
                "pull.completed",
                Map.of("operations", page.size()),
                clock.instant()));
        return new PullReplicationResult(
                page.stream().map(stored -> stored.operation()).toList(),
                cursors.encode(next));
    }
}
