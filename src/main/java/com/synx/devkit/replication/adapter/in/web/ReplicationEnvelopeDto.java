package com.synx.devkit.replication.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.synx.devkit.replication.domain.model.OperationType;
import com.synx.devkit.replication.domain.model.ReplicationEnvelope;

/** Exact JSON shape consumed and produced by the Go sync transport. */
public record ReplicationEnvelopeDto(
        @JsonProperty("record_id") String recordId,
        @JsonProperty("record_type") String recordType,
        @JsonProperty("key_version") long keyVersion,
        @JsonProperty("envelope_version") long envelopeVersion,
        @JsonProperty("account_id") String accountId,
        @JsonProperty("device_id") String deviceId,
        @JsonProperty("protocol_version") long protocolVersion,
        @JsonProperty("operation_id") String operationId,
        @JsonProperty("entity_version") long entityVersion,
        String operation,
        byte[] ciphertext) {

    public ReplicationEnvelope toDomain() {
        return new ReplicationEnvelope(
                recordId,
                recordType,
                keyVersion,
                envelopeVersion,
                accountId,
                deviceId,
                protocolVersion,
                operationId,
                entityVersion,
                OperationType.fromWire(operation),
                ciphertext);
    }

    public static ReplicationEnvelopeDto fromDomain(ReplicationEnvelope envelope) {
        return new ReplicationEnvelopeDto(
                envelope.recordId(),
                envelope.recordType(),
                envelope.keyVersion(),
                envelope.envelopeVersion(),
                envelope.accountId(),
                envelope.deviceId(),
                envelope.protocolVersion(),
                envelope.operationId(),
                envelope.entityVersion(),
                envelope.operation().wireValue(),
                envelope.ciphertext());
    }
}
