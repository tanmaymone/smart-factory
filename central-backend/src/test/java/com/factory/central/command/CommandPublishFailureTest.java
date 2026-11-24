package com.factory.central.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.factory.central.cache.LatestTagCache;
import com.factory.central.mqtt.MqttPublishException;
import com.factory.central.mqtt.MqttPublisher;
import com.factory.central.registry.Protocol;
import com.factory.central.registry.TagDataType;
import com.factory.central.registry.TagMapping;
import com.factory.central.stream.CommandUpdateEvent;
import com.factory.central.stream.TelemetryStreamHub;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@Tag("SF-M1.6")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommandPublishFailureTest {

    @Mock
    private CommandTagResolver tagResolver;

    @Mock
    private LatestTagCache latestTagCache;

    @Mock
    private InterlockEvaluator interlockEvaluator;

    @Mock
    private CommandAuditWriter auditWriter;

    @Mock
    private CommandAuditLogRepository auditLogRepository;

    @Mock
    private CommandRejectionRecorder rejectionRecorder;

    @Mock
    private MqttPublisher mqttPublisher;

    @Mock
    private TelemetryStreamHub streamHub;

    private CommandService service;
    private final Instant now = Instant.parse("2026-11-19T12:05:00Z");
    private final UUID commandId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private TagMapping tag;

    @BeforeEach
    void setUp() {
        service = new CommandService(
                tagResolver,
                latestTagCache,
                interlockEvaluator,
                auditWriter,
                auditLogRepository,
                rejectionRecorder,
                mqttPublisher,
                streamHub,
                new ObjectMapper(),
                Clock.fixed(now, ZoneOffset.UTC));
        tag = new TagMapping(
                UUID.randomUUID(),
                "INDORE",
                "LINE-01",
                "VALVE_101_POS",
                "valve",
                Protocol.OPC_UA,
                "opc.tcp://localhost",
                "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1001",
                TagDataType.FLOAT,
                "%",
                true,
                0.0,
                100.0,
                0.5,
                null,
                null,
                null,
                null,
                null);
        when(tagResolver.resolve(any())).thenReturn(tag);
        when(latestTagCache.get(any(), any(), any())).thenReturn(Optional.empty());
        when(interlockEvaluator.evaluate(any(), any())).thenReturn(Optional.empty());
        when(auditWriter.insert(any())).thenAnswer(invocation -> {
            CommandAuditLog log = invocation.getArgument(0);
            return new CommandAuditLog(
                    commandId,
                    log.getSiteId(),
                    log.getLineId(),
                    log.getTagCode(),
                    log.getRequestedValue(),
                    log.getPreviousValue(),
                    log.getRequestedBy(),
                    log.getReason(),
                    log.getStatus(),
                    log.getRejectionReason(),
                    log.getCreatedAt());
        });
    }

    @Test
    void publishFailureWithUpdateEmitsFailedEdge() throws Exception {
        doThrow(new MqttPublishException("broker down")).when(mqttPublisher).publish(any(), any());
        when(auditWriter.transitionFromDispatched(
                        eq(commandId), eq(CommandStatus.FAILED_EDGE), eq(null), any(), any()))
                .thenReturn(1);

        CommandRequest request = request();
        assertThatThrownBy(() -> service.submit(request))
                .isInstanceOf(CommandException.class)
                .satisfies(ex -> {
                    CommandException ce = (CommandException) ex;
                    assertThat(ce.getCode()).isEqualTo(CommandErrorCode.MQTT_PUBLISH_FAILED);
                    assertThat(ce.getMessage()).startsWith("MQTT publish failed:");
                });

        ArgumentCaptor<CommandUpdateEvent> captor = ArgumentCaptor.forClass(CommandUpdateEvent.class);
        verify(streamHub).publishCommand(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(CommandUpdateEvent.Status.FAILED_EDGE);
        assertThat(captor.getValue().errorMessage()).startsWith("MQTT publish failed:");
    }

    @Test
    void publishFailureWithZeroUpdateDoesNotEmitFailedEdge() throws Exception {
        doThrow(new MqttPublishException("broker down")).when(mqttPublisher).publish(any(), any());
        when(auditWriter.transitionFromDispatched(
                        eq(commandId), eq(CommandStatus.FAILED_EDGE), eq(null), any(), any()))
                .thenReturn(0);
        when(auditWriter.require(commandId))
                .thenReturn(new CommandAuditLog(
                        commandId,
                        "INDORE",
                        "LINE-01",
                        "VALVE_101_POS",
                        "50.0",
                        null,
                        "op",
                        "reason",
                        CommandStatus.ACKED,
                        null,
                        now));

        CommandAcceptedResponse response = service.submit(request());
        assertThat(response.status()).isEqualTo(CommandStatus.ACKED.asString());
        verify(streamHub, never()).publishCommand(any());
    }

    private CommandRequest request() throws Exception {
        return new CommandRequest(
                "INDORE",
                "LINE-01",
                null,
                "VALVE_101_POS",
                new ObjectMapper().readTree("50.0"),
                "operator@factory.com",
                "set valve");
    }
}
