package com.synx.devkit.replication.application.service;

import com.synx.devkit.audit.application.port.out.AuditEventSink;
import com.synx.devkit.audit.domain.AuditEvent;
import com.synx.devkit.replication.application.port.in.PushConflict;
import com.synx.devkit.replication.application.port.in.PushReplicationCommand;
import com.synx.devkit.replication.application.port.in.PushReplicationResult;
import com.synx.devkit.replication.application.port.in.PushReplicationUseCase;
import com.synx.devkit.replication.application.port.out.EntityHeadRepository;
import com.synx.devkit.replication.application.port.out.ReplicationLock;
import com.synx.devkit.replication.application.port.out.ReplicationLogRepository;
import com.synx.devkit.replication.domain.model.ArbitrationDecision;
import com.synx.devkit.replication.domain.model.EntityHead;
import com.synx.devkit.replication.domain.service.ArbitrationPolicy;
import com.synx.devkit.shared.application.port.out.TransactionRunner;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Orchestrates one atomic push batch; all database work shares one transaction. */
public final class PushReplicationService implements PushReplicationUseCase {
    private final ReplicationRequestValidator validator;
    private final OperationDigestService digests;
    private final ArbitrationPolicy arbitration;
    private final ReplicationLock locks;
    private final ReplicationLogRepository log;
    private final EntityHeadRepository heads;
    private final AuditEventSink audit;
    private final TransactionRunner transactions;
    private final Clock clock;

    public PushReplicationService(
            ReplicationRequestValidator validator,
            OperationDigestService digests,
            ArbitrationPolicy arbitration,
            ReplicationLock locks,
            ReplicationLogRepository log,
            EntityHeadRepository heads,
            AuditEventSink audit,
            TransactionRunner transactions,
            Clock clock) {
        this.validator = validator;
        this.digests = digests;
        this.arbitration = arbitration;
        this.locks = locks;
        this.log = log;
        this.heads = heads;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public PushReplicationResult push(PushReplicationCommand command) {
        validator.validateBatch(command.context(), command.operations());
        return transactions.required(() -> process(command));
    }

    private PushReplicationResult process(PushReplicationCommand command) {
        var recordIds = new LinkedHashSet<String>();
        command.operations().forEach(operation -> recordIds.add(operation.envelope().recordId()));
        locks.lockAll(command.context().accountId(), recordIds);

        var sent = new ArrayList<String>();
        var conflicts = new ArrayList<PushConflict>();
        int replayed = 0;
        for (var operation : command.operations()) {
            var digest = digests.digest(operation);
            var decision = arbitration.decide(
                    heads.find(command.context().accountId(), operation.envelope().recordId()),
                    log.findByOperationId(command.context().accountId(), operation.envelope().operationId()),
                    log.findByIdempotencyKey(command.context().accountId(), operation.idempotencyKey()),
                    operation,
                    digest);

            if (decision.type() == ArbitrationDecision.Type.REPLAY) {
                sent.add(operation.idempotencyKey());
                replayed++;
                continue;
            }
            if (decision.type() == ArbitrationDecision.Type.CONFLICT) {
                conflicts.add(new PushConflict(
                        UUID.randomUUID().toString(),
                        operation.envelope().recordId(),
                        operation.envelope().recordType(),
                        operation.idempotencyKey(),
                        decision.remoteIdempotencyKey(),
                        clock.instant()));
                continue;
            }

            long sequence = log.append(command.context().accountId(), operation, digest, clock.instant());
            heads.save(new EntityHead(
                    command.context().accountId(),
                    operation.envelope().recordId(),
                    operation.envelope().recordType(),
                    operation.envelope().entityVersion(),
                    sequence,
                    operation.idempotencyKey(),
                    clock.instant()));
            sent.add(operation.idempotencyKey());
        }

        audit.record(new AuditEvent(
                command.requestId(),
                command.context().accountId(),
                command.context().deviceId(),
                "push.completed",
                Map.of(
                        "accepted", sent.size() - replayed,
                        "replayed", replayed,
                        "conflicted", conflicts.size()),
                clock.instant()));
        return new PushReplicationResult(List.copyOf(sent), List.copyOf(conflicts));
    }
}
