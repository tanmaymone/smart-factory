package com.factory.edge.opcua;

import java.time.Instant;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;

public record TagReading(
        String event,
        String siteId,
        String lineId,
        String tagCode,
        String nodeId,
        Object value,
        String quality,
        Instant sourceTimestamp,
        Instant serverTimestamp,
        String statusName,
        Boolean nodeWritable) {

    public static final String EVENT_READ = "READ";
    public static final String EVENT_CHANGE = "CHANGE";

    public static TagReading from(
            String event, OpcUaProperties.TagConfig tag, DataValue dataValue) {
        return from(event, tag, dataValue, null);
    }

    public static TagReading from(
            String event,
            OpcUaProperties.TagConfig tag,
            DataValue dataValue,
            Boolean nodeWritable) {
        StatusCode status = dataValue.getStatusCode();
        if (status == null) {
            status = StatusCode.BAD;
        }
        Variant variant = dataValue.getValue();
        Object raw = variant == null || variant.isNull() ? null : variant.getValue();
        return new TagReading(
                event,
                tag.getSiteId(),
                tag.getLineId(),
                tag.getTagCode(),
                tag.getNodeId(),
                raw,
                qualityOf(status),
                toInstant(dataValue.getSourceTime()),
                toInstant(dataValue.getServerTime()),
                status.isGood() ? null : symbolicName(status),
                nodeWritable);
    }

    static String qualityOf(StatusCode status) {
        if (status.isGood()) {
            return "GOOD";
        }
        if (status.isUncertain()) {
            return "UNCERTAIN";
        }
        return "BAD";
    }

    public static String symbolicName(StatusCode status) {
        return StatusCodes.lookup(status.getValue())
                .map(nameAndDesc -> nameAndDesc[0])
                .orElse("0x%08X".formatted(status.getValue()));
    }

    private static Instant toInstant(DateTime dateTime) {
        if (dateTime == null || dateTime.isNull()) {
            return null;
        }
        return dateTime.getJavaInstant();
    }
}
