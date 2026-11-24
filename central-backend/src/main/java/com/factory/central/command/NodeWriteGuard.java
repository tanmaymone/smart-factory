package com.factory.central.command;

import com.factory.central.registry.TagDataType;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * Rejects a governed write before MQTT when the latest sample already shows the OPC UA node
 * cannot accept the registry datatype or is not writable.
 */
public final class NodeWriteGuard {

    private NodeWriteGuard() {}

    public static void check(TagDataType registryType, String observedDataType, Boolean nodeWritable) {
        String reason = mismatch(registryType, observedDataType);
        if (reason != null) {
            throw rejection(CommandErrorCode.NODE_TYPE_MISMATCH, reason, "dataType");
        }
        if (Boolean.FALSE.equals(nodeWritable)) {
            throw rejection(
                    CommandErrorCode.NODE_NOT_WRITABLE,
                    "OPC UA node is not writable",
                    "externalAddress");
        }
    }

    static String mismatch(TagDataType registryType, String observedDataType) {
        if (registryType == null || observedDataType == null || observedDataType.isBlank()) {
            return null;
        }
        String observed = observedDataType.trim().toUpperCase(Locale.ROOT);
        if (registryType.name().equals(observed)) {
            return null;
        }
        return switch (registryType) {
            case FLOAT -> "FLOAT requires Float or Double node datatype";
            case INT -> "INT requires an integer node datatype";
            case BOOLEAN -> "BOOLEAN requires Boolean node datatype";
            case STRING -> "STRING requires String node datatype";
        };
    }

    private static CommandException rejection(String code, String reason, String field) {
        return new CommandException(
                code,
                reason,
                HttpStatus.UNPROCESSABLE_ENTITY,
                null,
                List.of(new CommandErrorResponse.FieldErrorDetail(field, reason)));
    }
}
