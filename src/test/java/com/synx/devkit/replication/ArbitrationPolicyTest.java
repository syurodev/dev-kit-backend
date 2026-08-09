package com.synx.devkit.replication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.synx.devkit.replication.application.service.OperationDigestService;
import com.synx.devkit.replication.domain.model.ArbitrationDecision;
import com.synx.devkit.replication.domain.model.ContentDigest;
import com.synx.devkit.replication.domain.model.EntityHead;
import com.synx.devkit.replication.domain.model.OperationType;
import com.synx.devkit.replication.domain.model.ReplicationEnvelope;
import com.synx.devkit.replication.domain.model.ReplicationOperation;
import com.synx.devkit.replication.domain.model.StoredOperation;
import com.synx.devkit.replication.domain.service.ArbitrationPolicy;
import com.synx.devkit.shared.error.ValidationException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ArbitrationPolicyTest {
    private final ArbitrationPolicy policy = new ArbitrationPolicy();
    private final OperationDigestService digests = new OperationDigestService();

    @Test
    void acceptsFirstAndNextVersion() {
        var first = operation("idem-1", "op-1", 1);
        assertEquals(ArbitrationDecision.Type.ACCEPT, decide(Optional.empty(), first).type());

        var head = head(1, "idem-1");
        var next = operation("idem-2", "op-2", 2);
        assertEquals(ArbitrationDecision.Type.ACCEPT, decide(Optional.of(head), next).type());
    }

    @Test
    void staleVersionConflictsAndGapRejects() {
        var head = head(2, "remote-key");
        var stale = operation("local-key", "op-stale", 2);
        var conflict = decide(Optional.of(head), stale);
        assertEquals(ArbitrationDecision.Type.CONFLICT, conflict.type());
        assertEquals("remote-key", conflict.remoteIdempotencyKey());

        var gap = operation("idem-gap", "op-gap", 4);
        assertThrows(ValidationException.class, () -> decide(Optional.of(head), gap));
    }

    @Test
    void exactReplayIsAcknowledgedButChangedContentIsRejected() {
        var operation = operation("idem-1", "op-1", 1);
        ContentDigest digest = digests.digest(operation);
        var stored = new StoredOperation(1, UUID.randomUUID(), operation, digest, Instant.now());
        var replay = policy.decide(
                Optional.of(head(1, "idem-1")),
                Optional.of(stored),
                Optional.of(stored),
                operation,
                digest);
        assertEquals(ArbitrationDecision.Type.REPLAY, replay.type());

        var changed = operation("idem-1", "op-1", 2);
        assertThrows(ValidationException.class, () -> policy.decide(
                Optional.of(head(1, "idem-1")),
                Optional.of(stored),
                Optional.of(stored),
                changed,
                digests.digest(changed)));
    }

    private ArbitrationDecision decide(Optional<EntityHead> head, ReplicationOperation incoming) {
        return policy.decide(head, Optional.empty(), Optional.empty(), incoming, digests.digest(incoming));
    }

    private static EntityHead head(long version, String idempotencyKey) {
        return new EntityHead(
                UUID.randomUUID(), "record-1", "note", version, version, idempotencyKey, Instant.now());
    }

    private static ReplicationOperation operation(String idempotencyKey, String operationId, long version) {
        return new ReplicationOperation(
                idempotencyKey,
                OperationType.UPDATE,
                new ReplicationEnvelope(
                        "record-1", "note", 1, 2,
                        UUID.randomUUID().toString(), "device-1", 1,
                        operationId, version, OperationType.UPDATE, new byte[]{1, 2, 3}));
    }
}
