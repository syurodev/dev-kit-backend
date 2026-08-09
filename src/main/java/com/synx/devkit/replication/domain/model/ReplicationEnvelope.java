package com.synx.devkit.replication.domain.model;

/** Public routing metadata plus ciphertext that remains opaque to the server. */
public record ReplicationEnvelope(
        String recordId,
        String recordType,
        long keyVersion,
        long envelopeVersion,
        String accountId,
        String deviceId,
        long protocolVersion,
        String operationId,
        long entityVersion,
        OperationType operation,
        byte[] ciphertext) {

    public ReplicationEnvelope {
        ciphertext = ciphertext == null ? null : ciphertext.clone();
    }

    @Override
    public byte[] ciphertext() {
        return ciphertext == null ? null : ciphertext.clone();
    }
}
