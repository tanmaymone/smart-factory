package com.factory.edge.command;

import com.factory.edge.command.CommandValueCoercer.CoerceFail;
import com.factory.edge.command.CommandValueCoercer.CoerceOk;
import com.factory.edge.opcua.NodeIdParser;
import com.factory.edge.opcua.OpcUaProbeException;
import com.factory.edge.opcua.OpcUaSession;
import com.factory.edge.opcua.TagReading;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.springframework.stereotype.Component;

@Component
public class OpcUaCommandWriter implements CommandWriter {

    private final OpcUaSession session;

    public OpcUaCommandWriter(OpcUaSession session) {
        this.session = session;
    }

    @Override
    public CommandResult write(
            String commandId,
            String siteId,
            String lineId,
            String tagCode,
            String nodeIdText,
            String dataType,
            JsonNode targetValue,
            Instant executedAt) {
        Object target = jsonValue(targetValue);
        if (!session.isAvailable()) {
            return CommandResult.rejected(
                    commandId,
                    siteId,
                    lineId,
                    tagCode,
                    nodeIdText,
                    target,
                    "OPC UA session unavailable",
                    executedAt,
                    false);
        }

        try {
            NodeId nodeId = NodeIdParser.parse(nodeIdText);
            DataValue typeAttr = session.readAttribute(nodeId, AttributeId.DataType);
            if (bad(typeAttr.getStatusCode())) {
                return rejectStatus(
                        commandId, siteId, lineId, tagCode, nodeIdText, target, typeAttr.getStatusCode(), executedAt);
            }
            Object coerced =
                    CommandValueCoercer.coerce(dataType, targetValue, extractNodeId(typeAttr));
            if (coerced instanceof CoerceFail fail) {
                return CommandResult.rejected(
                        commandId, siteId, lineId, tagCode, nodeIdText, target, fail.reason(), executedAt, true);
            }

            StatusCode writeStatus = session.writeValue(nodeId, ((CoerceOk) coerced).variant());
            if (bad(writeStatus)) {
                return rejectStatus(
                        commandId, siteId, lineId, tagCode, nodeIdText, target, writeStatus, executedAt);
            }

            DataValue readback = session.readValue(nodeId);
            if (bad(readback.getStatusCode())) {
                return rejectStatus(
                        commandId,
                        siteId,
                        lineId,
                        tagCode,
                        nodeIdText,
                        target,
                        readback.getStatusCode(),
                        executedAt);
            }
            Object observed =
                    readback.getValue() == null || readback.getValue().isNull()
                            ? null
                            : readback.getValue().getValue();
            return CommandResult.acked(
                    commandId, siteId, lineId, tagCode, nodeIdText, target, observed, executedAt);
        } catch (OpcUaProbeException e) {
            String msg = e.getMessage() == null ? "OPC UA session unavailable" : e.getMessage();
            boolean transientFail = msg.toLowerCase().contains("session unavailable") || isTransientMessage(msg);
            return CommandResult.rejected(
                    commandId, siteId, lineId, tagCode, nodeIdText, target, msg, executedAt, !transientFail);
        }
    }

    private static CommandResult rejectStatus(
            String commandId,
            String siteId,
            String lineId,
            String tagCode,
            String nodeIdText,
            Object target,
            StatusCode status,
            Instant executedAt) {
        String name = status == null ? "Bad" : TagReading.symbolicName(status);
        return CommandResult.rejected(
                commandId, siteId, lineId, tagCode, nodeIdText, target, name, executedAt, !isTransient(status));
    }

    private static NodeId extractNodeId(DataValue typeAttr) {
        Variant v = typeAttr.getValue();
        if (v == null || v.isNull() || !(v.getValue() instanceof NodeId id)) {
            return null;
        }
        return id;
    }

    private static Object jsonValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.isIntegralNumber() ? node.longValue() : node.doubleValue();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isTextual()) {
            return node.asText();
        }
        return node.toString();
    }

    private static boolean bad(StatusCode status) {
        return status == null || status.isBad();
    }

    private static boolean isTransient(StatusCode status) {
        if (status == null) {
            return true;
        }
        long code = status.getValue();
        return code == StatusCodes.Bad_SessionClosed
                || code == StatusCodes.Bad_ConnectionClosed
                || code == StatusCodes.Bad_Timeout
                || code == StatusCodes.Bad_NoCommunication
                || code == StatusCodes.Bad_ServerNotConnected;
    }

    private static boolean isTransientMessage(String msg) {
        String lower = msg.toLowerCase();
        return lower.contains("timeout") || lower.contains("connection") || lower.contains("session");
    }
}
