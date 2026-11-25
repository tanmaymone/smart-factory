package com.factory.edge.opcua;

import com.factory.edge.buffer.StoreAndForwardQueue;
import com.factory.edge.buffer.TelemetryRecord;
import com.factory.edge.filter.DeadbandFilter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Deadband gate, diagnostics fan-out, and offline-buffer hand-off for every OPC UA reading. */
final class ReadingPipeline {

    private static final Logger log = LoggerFactory.getLogger(ReadingPipeline.class);

    private final OpcUaDiagnosticsSink diagnostics;
    private final StoreAndForwardQueue queue;

    private volatile DeadbandFilter filter = new DeadbandFilter(List.of());

    ReadingPipeline(OpcUaDiagnosticsSink diagnostics, StoreAndForwardQueue queue) {
        this.diagnostics = diagnostics;
        this.queue = queue;
    }

    DeadbandFilter filter() {
        return filter;
    }

    void replaceFilter(DeadbandFilter next) {
        this.filter = next;
    }

    void accept(TagReading reading) {
        boolean emit = filter.shouldEmit(reading);
        diagnostics.onReading(reading, emit);
        if (!emit) {
            return;
        }
        String line = TagReadingFormatter.format(reading);
        if ("BAD".equals(reading.quality())) {
            log.error(line);
        } else if ("UNCERTAIN".equals(reading.quality())) {
            log.warn(line);
        } else {
            log.info(line);
        }
        queue.offer(TelemetryRecord.from(reading));
    }
}
