package com.factory.central.stream;

/**
 * Narrow outbound port for {@code COMMAND_UPDATE} SSE events. Implemented by {@link TelemetryStreamHub};
 * command module depends on this interface, not stream internals.
 */
public interface CommandStreamSink {

    void publishCommand(CommandUpdateEvent event);
}