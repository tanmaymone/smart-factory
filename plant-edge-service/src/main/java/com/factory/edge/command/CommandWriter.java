package com.factory.edge.command;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** Port for protocol-specific setpoint writes (OPC UA now; S7/Modbus later). */
public interface CommandWriter {

    CommandResult write(
            String commandId,
            String siteId,
            String lineId,
            String tagCode,
            String nodeIdText,
            String dataType,
            JsonNode targetValue,
            Instant executedAt);
}
