package com.factory.central.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.factory.central.stream.CommandUpdateEvent;
import com.factory.central.stream.CommandStreamSink;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("SF-M1.6")
@ExtendWith(MockitoExtension.class)
class CommandAckServiceTest {

    @Mock
    private CommandAuditLogRepository auditLogRepository;

    @Mock
    private CommandAuditWriter auditWriter;

    @Mock
    private CommandStreamSink commandStreamSink;

    private CommandAckService service;
    private final Instant now = Instant.parse("2026-11-19T12:05:00Z");
    private final UUID commandId = UUID.fromString("c4a760a8-dbcf-4e14-9f39-645a8e937f12");

    @BeforeEach
    void setUp() {
        service = new CommandAckService(
                auditLogRepository, auditWriter, commandStreamSink, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void duplicateAckEmitsSseOnlyOnce() {
        CommandAuditLog row = dispatched();
        when(auditLogRepository.findByCommandId(commandId)).thenReturn(Optional.of(row));
        when(auditWriter.transitionFromDispatched(
                        eq(commandId),
                        eq(CommandStatus.ACKED),
                        eq("50.0"),
                        eq(null),
                        any()))
                .thenReturn(1)
                .thenReturn(0);
        when(auditWriter.require(commandId)).thenReturn(row);

        service.applyAck("INDORE", "LINE-01", commandId, "INDORE", "LINE-01", "ACKED", 50.0, null, now);
        service.applyAck("INDORE", "LINE-01", commandId, "INDORE", "LINE-01", "ACKED", 50.0, null, now);

        ArgumentCaptor<CommandUpdateEvent> captor = ArgumentCaptor.forClass(CommandUpdateEvent.class);
        verify(commandStreamSink).publishCommand(captor.capture());
        assertThat(captor.getAllValues()).hasSize(1);
        assertThat(captor.getValue().status()).isEqualTo(CommandUpdateEvent.Status.ACKED);
    }

    @Test
    void unknownCommandIdDoesNotEmit() {
        when(auditLogRepository.findByCommandId(commandId)).thenReturn(Optional.empty());
        service.applyAck("INDORE", "LINE-01", commandId, "INDORE", "LINE-01", "ACKED", 50.0, null, now);
        verify(auditWriter, never()).transitionFromDispatched(any(), any(), any(), any(), any());
        verify(commandStreamSink, never()).publishCommand(any());
    }

    private CommandAuditLog dispatched() {
        return new CommandAuditLog(
                commandId,
                "INDORE",
                "LINE-01",
                "VALVE_101_POS",
                "50.0",
                "10.0",
                "operator@factory.com",
                "open valve",
                CommandStatus.VALIDATED_DISPATCHED,
                null,
                now);
    }
}
