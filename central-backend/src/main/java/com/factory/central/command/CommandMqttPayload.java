package com.factory.central.command;

import java.time.Instant;
import java.util.UUID;

public record CommandMqttPayload(
        UUID commandId,
        String siteId,
        String lineId,
        String tagCode,
        String protocol,
        String externalAddress,
        String dataType,
        Object targetValue,
        String issuedBy,
        Instant issuedAt) {}
