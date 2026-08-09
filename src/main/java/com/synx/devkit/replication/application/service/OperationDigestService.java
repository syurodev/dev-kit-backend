package com.synx.devkit.replication.application.service;

import com.synx.devkit.replication.domain.model.ContentDigest;
import com.synx.devkit.replication.domain.model.ReplicationOperation;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Computes a stable replay fingerprint independent of JSON field ordering.
 * Every variable field is length-prefixed to avoid concatenation ambiguity.
 */
public final class OperationDigestService {
    public ContentDigest digest(ReplicationOperation operation) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            put(digest, operation.idempotencyKey());
            put(digest, operation.operation().wireValue());
            var envelope = operation.envelope();
            put(digest, envelope.recordId());
            put(digest, envelope.recordType());
            put(digest, envelope.keyVersion());
            put(digest, envelope.envelopeVersion());
            put(digest, envelope.accountId());
            put(digest, envelope.deviceId());
            put(digest, envelope.protocolVersion());
            put(digest, envelope.operationId());
            put(digest, envelope.entityVersion());
            put(digest, envelope.operation().wireValue());
            put(digest, envelope.ciphertext());
            return new ContentDigest(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void put(MessageDigest digest, String value) {
        put(digest, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void put(MessageDigest digest, long value) {
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(value).array());
    }

    private static void put(MessageDigest digest, byte[] value) {
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value.length).array());
        digest.update(value);
    }
}
