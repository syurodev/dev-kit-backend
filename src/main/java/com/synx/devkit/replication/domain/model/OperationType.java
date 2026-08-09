package com.synx.devkit.replication.domain.model;

import com.synx.devkit.shared.error.ValidationException;
import java.util.Locale;

public enum OperationType {
    CREATE,
    UPDATE,
    DELETE;

    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static OperationType fromWire(String value) {
        if (value == null) {
            throw new ValidationException("operation is required");
        }
        return switch (value) {
            case "create" -> CREATE;
            case "update" -> UPDATE;
            case "delete" -> DELETE;
            default -> throw new ValidationException("operation is invalid");
        };
    }
}
