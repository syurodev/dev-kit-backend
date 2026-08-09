package com.synx.devkit.replication.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.synx.devkit.replication.domain.model.OperationType;
import com.synx.devkit.replication.domain.model.ReplicationOperation;

public record PushOperationDto(
        @JsonProperty("idempotency_key") String idempotencyKey,
        String operation,
        ReplicationEnvelopeDto envelope) {

    public ReplicationOperation toDomain() {
        return new ReplicationOperation(
                idempotencyKey,
                OperationType.fromWire(operation),
                envelope == null ? null : envelope.toDomain());
    }

    public static PushOperationDto fromDomain(ReplicationOperation operation) {
        return new PushOperationDto(
                operation.idempotencyKey(),
                operation.operation().wireValue(),
                ReplicationEnvelopeDto.fromDomain(operation.envelope()));
    }
}
