package com.synx.devkit.replication.application.service;

import com.synx.devkit.identity.application.port.in.AuthorizedSyncContext;
import com.synx.devkit.replication.domain.model.ReplicationOperation;
import com.synx.devkit.shared.domain.SyncIdentifier;
import com.synx.devkit.shared.domain.SyncProtocol;
import com.synx.devkit.shared.domain.WireLimits;
import com.synx.devkit.shared.error.ValidationException;
import java.util.HashSet;
import java.util.List;

/** Mirrors the client-side validation before any database transaction begins. */
public final class ReplicationRequestValidator {
    public void validateBatch(AuthorizedSyncContext context, List<ReplicationOperation> operations) {
        if (operations == null || operations.isEmpty() || operations.size() > WireLimits.MAX_OPERATIONS) {
            throw new ValidationException("push batch size is invalid");
        }
        var idempotencyKeys = new HashSet<String>();
        var operationIds = new HashSet<String>();
        for (ReplicationOperation operation : operations) {
            validate(context, operation);
            if (!idempotencyKeys.add(operation.idempotencyKey())) {
                throw new ValidationException("duplicate idempotency key in push batch");
            }
            if (!operationIds.add(operation.envelope().operationId())) {
                throw new ValidationException("duplicate operation id in push batch");
            }
        }
    }

    public void validate(AuthorizedSyncContext context, ReplicationOperation operation) {
        if (operation == null || operation.envelope() == null || operation.operation() == null) {
            throw new ValidationException("replication operation is required");
        }
        var envelope = operation.envelope();
        // Idempotency keys are opaque, namespaced hashes. Unlike record and
        // device identifiers, they are never interpreted as path segments.
        SyncIdentifier.requireOpaque("idempotency key", operation.idempotencyKey(), WireLimits.MAX_IDENTIFIER_LENGTH);
        SyncIdentifier.require("record id", envelope.recordId(), WireLimits.MAX_IDENTIFIER_LENGTH);
        SyncIdentifier.require("record type", envelope.recordType(), WireLimits.MAX_RECORD_TYPE_LENGTH);
        SyncIdentifier.require("account id", envelope.accountId(), WireLimits.MAX_IDENTIFIER_LENGTH);
        SyncIdentifier.require("device id", envelope.deviceId(), WireLimits.MAX_IDENTIFIER_LENGTH);
        SyncIdentifier.require("operation id", envelope.operationId(), WireLimits.MAX_IDENTIFIER_LENGTH);
        if (envelope.keyVersion() <= 0 || envelope.keyVersion() > SyncProtocol.MAX_UNSIGNED_INT) {
            throw new ValidationException("key version is invalid");
        }
        if (envelope.entityVersion() <= 0 || envelope.entityVersion() > SyncProtocol.MAX_UNSIGNED_INT) {
            throw new ValidationException("entity version is invalid");
        }
        if (envelope.envelopeVersion() != SyncProtocol.ENVELOPE_VERSION) {
            throw new ValidationException("replication envelope version is unsupported");
        }
        if (envelope.protocolVersion() != SyncProtocol.PROTOCOL_VERSION) {
            throw new ValidationException("sync protocol version is unsupported");
        }
        if (!context.accountId().toString().equals(envelope.accountId())) {
            throw new ValidationException("envelope account does not match authenticated account");
        }
        if (!context.deviceId().equals(envelope.deviceId())) {
            throw new ValidationException("envelope device does not match authenticated device");
        }
        if (operation.operation() != envelope.operation()) {
            throw new ValidationException("operation does not match envelope");
        }
        byte[] ciphertext = envelope.ciphertext();
        if (ciphertext == null || ciphertext.length == 0 || ciphertext.length > WireLimits.MAX_CIPHERTEXT_BYTES) {
            throw new ValidationException("replication ciphertext size is invalid");
        }
    }
}
