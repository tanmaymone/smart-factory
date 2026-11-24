package com.factory.edge.command;

import com.fasterxml.jackson.databind.JsonNode;
import org.eclipse.milo.opcua.stack.core.NodeIds;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;

/**
 * Strict command → OPC UA Variant coercion. Rejects silent narrowing and non-built-in DataTypes.
 */
public final class CommandValueCoercer {

    public record CoerceOk(Variant variant) {}

    public record CoerceFail(String reason) {}

    private CommandValueCoercer() {}

    public static Object coerce(String dataType, JsonNode targetValue, NodeId nodeDataType) {
        if (targetValue == null || targetValue.isNull()) {
            return new CoerceFail("targetValue is required");
        }
        if (dataType == null || dataType.isBlank()) {
            return new CoerceFail("dataType is required");
        }
        if (nodeDataType == null || !isBuiltIn(nodeDataType)) {
            return new CoerceFail("unsupported node datatype");
        }
        return switch (dataType.trim().toUpperCase()) {
            case "FLOAT" -> coerceFloat(targetValue, nodeDataType);
            case "INT" -> coerceInt(targetValue, nodeDataType);
            case "BOOLEAN" -> coerceBoolean(targetValue, nodeDataType);
            case "STRING" -> coerceString(targetValue, nodeDataType);
            default -> new CoerceFail("unsupported dataType " + dataType);
        };
    }

    private static Object coerceFloat(JsonNode target, NodeId nodeDataType) {
        if (target.isTextual() || target.isBoolean() || !target.isNumber()) {
            return new CoerceFail("FLOAT targetValue must be a finite number");
        }
        double d = target.asDouble();
        if (!Double.isFinite(d)) {
            return new CoerceFail("FLOAT targetValue must be a finite number");
        }
        if (NodeIds.Float.equals(nodeDataType)) {
            float f = (float) d;
            if (!Float.isFinite(f)) {
                return new CoerceFail("FLOAT targetValue overflows Float");
            }
            return new CoerceOk(new Variant(f));
        }
        if (NodeIds.Double.equals(nodeDataType)) {
            return new CoerceOk(new Variant(d));
        }
        return new CoerceFail("FLOAT requires Float or Double node datatype");
    }

    private static Object coerceInt(JsonNode target, NodeId nodeDataType) {
        if (target.isTextual() || target.isBoolean() || !target.isNumber()) {
            return new CoerceFail("INT targetValue must be a whole number");
        }
        double d = target.asDouble();
        if (!Double.isFinite(d) || d != Math.rint(d)) {
            return new CoerceFail("INT targetValue must be a whole number");
        }
        long whole = (long) d;
        if (NodeIds.SByte.equals(nodeDataType)) {
            return inRange(whole, Byte.MIN_VALUE, Byte.MAX_VALUE)
                    ? new CoerceOk(new Variant((byte) whole))
                    : rangeFail();
        }
        if (NodeIds.Byte.equals(nodeDataType)) {
            return inRange(whole, 0, 255)
                    ? new CoerceOk(new Variant(Unsigned.ubyte((int) whole)))
                    : rangeFail();
        }
        if (NodeIds.Int16.equals(nodeDataType)) {
            return inRange(whole, Short.MIN_VALUE, Short.MAX_VALUE)
                    ? new CoerceOk(new Variant((short) whole))
                    : rangeFail();
        }
        if (NodeIds.UInt16.equals(nodeDataType)) {
            return inRange(whole, 0, 65535)
                    ? new CoerceOk(new Variant(Unsigned.ushort((int) whole)))
                    : rangeFail();
        }
        if (NodeIds.Int32.equals(nodeDataType)) {
            return inRange(whole, Integer.MIN_VALUE, Integer.MAX_VALUE)
                    ? new CoerceOk(new Variant((int) whole))
                    : rangeFail();
        }
        if (NodeIds.UInt32.equals(nodeDataType)) {
            return inRange(whole, 0, 0xFFFF_FFFFL)
                    ? new CoerceOk(new Variant(Unsigned.uint(whole)))
                    : rangeFail();
        }
        if (NodeIds.Int64.equals(nodeDataType)) {
            return new CoerceOk(new Variant(whole));
        }
        if (NodeIds.UInt64.equals(nodeDataType)) {
            if (whole < 0) {
                return rangeFail();
            }
            return new CoerceOk(new Variant(Unsigned.ulong(whole)));
        }
        return new CoerceFail("INT requires an integer node datatype");
    }

    private static Object coerceBoolean(JsonNode target, NodeId nodeDataType) {
        if (!NodeIds.Boolean.equals(nodeDataType)) {
            return new CoerceFail("BOOLEAN requires Boolean node datatype");
        }
        if (!target.isBoolean()) {
            return new CoerceFail("BOOLEAN targetValue must be a JSON boolean");
        }
        return new CoerceOk(new Variant(target.asBoolean()));
    }

    private static Object coerceString(JsonNode target, NodeId nodeDataType) {
        if (!NodeIds.String.equals(nodeDataType)) {
            return new CoerceFail("STRING requires String node datatype");
        }
        if (!target.isTextual()) {
            return new CoerceFail("STRING targetValue must be a JSON string");
        }
        return new CoerceOk(new Variant(target.asText()));
    }

    private static boolean isBuiltIn(NodeId nodeDataType) {
        return NodeIds.Boolean.equals(nodeDataType)
                || NodeIds.SByte.equals(nodeDataType)
                || NodeIds.Byte.equals(nodeDataType)
                || NodeIds.Int16.equals(nodeDataType)
                || NodeIds.UInt16.equals(nodeDataType)
                || NodeIds.Int32.equals(nodeDataType)
                || NodeIds.UInt32.equals(nodeDataType)
                || NodeIds.Int64.equals(nodeDataType)
                || NodeIds.UInt64.equals(nodeDataType)
                || NodeIds.Float.equals(nodeDataType)
                || NodeIds.Double.equals(nodeDataType)
                || NodeIds.String.equals(nodeDataType);
    }

    private static boolean inRange(long value, long min, long max) {
        return value >= min && value <= max;
    }

    private static CoerceFail rangeFail() {
        return new CoerceFail("INT targetValue out of range for node datatype");
    }
}
