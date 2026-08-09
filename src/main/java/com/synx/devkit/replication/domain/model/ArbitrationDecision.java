package com.synx.devkit.replication.domain.model;

/** Pure domain result; only conflicts need the current remote head key. */
public record ArbitrationDecision(Type type, String remoteIdempotencyKey) {
    public enum Type {
        ACCEPT,
        REPLAY,
        CONFLICT
    }

    public static ArbitrationDecision accept() {
        return new ArbitrationDecision(Type.ACCEPT, null);
    }

    public static ArbitrationDecision replay() {
        return new ArbitrationDecision(Type.REPLAY, null);
    }

    public static ArbitrationDecision conflict(String remoteIdempotencyKey) {
        return new ArbitrationDecision(Type.CONFLICT, remoteIdempotencyKey);
    }
}
