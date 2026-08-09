package com.synx.devkit.replication.domain.service;

import com.synx.devkit.replication.domain.model.ArbitrationDecision;
import com.synx.devkit.replication.domain.model.ContentDigest;
import com.synx.devkit.replication.domain.model.EntityHead;
import com.synx.devkit.replication.domain.model.ReplicationOperation;
import com.synx.devkit.replication.domain.model.StoredOperation;
import com.synx.devkit.shared.error.ValidationException;
import java.util.Optional;

/**
 * Stateless version arbitration shared by unit tests and the push use case.
 * Database locking is deliberately outside this domain policy.
 */
public final class ArbitrationPolicy {
    public ArbitrationDecision decide(
            Optional<EntityHead> head,
            Optional<StoredOperation> existingOperation,
            Optional<StoredOperation> existingIdempotency,
            ReplicationOperation incoming,
            ContentDigest incomingDigest) {
        if (existingOperation.isPresent()) {
            StoredOperation stored = existingOperation.orElseThrow();
            if (!stored.operation().idempotencyKey().equals(incoming.idempotencyKey())
                    || !stored.contentDigest().sameAs(incomingDigest)) {
                throw new ValidationException("operation replay does not match stored content");
            }
            return ArbitrationDecision.replay();
        }
        if (existingIdempotency.isPresent()) {
            throw new ValidationException("idempotency key belongs to another operation");
        }

        long incomingVersion = incoming.envelope().entityVersion();
        if (head.isEmpty()) {
            if (incomingVersion != 1L) {
                throw new ValidationException("entity version has a gap");
            }
            return ArbitrationDecision.accept();
        }

        EntityHead current = head.orElseThrow();
        if (!current.recordType().equals(incoming.envelope().recordType())) {
            throw new ValidationException("record type does not match entity head");
        }
        if (incomingVersion == current.currentEntityVersion() + 1L) {
            return ArbitrationDecision.accept();
        }
        if (incomingVersion <= current.currentEntityVersion()) {
            if (current.headIdempotencyKey().equals(incoming.idempotencyKey())) {
                // A reused head key with another operation ID was already
                // rejected by the idempotency lookup. Keep this invariant here
                // as a final defense before building client conflict metadata.
                throw new ValidationException("idempotency key conflicts with entity head");
            }
            return ArbitrationDecision.conflict(current.headIdempotencyKey());
        }
        throw new ValidationException("entity version has a gap");
    }
}
