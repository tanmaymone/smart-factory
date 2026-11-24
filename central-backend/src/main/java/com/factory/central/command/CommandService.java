package com.factory.central.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.factory.central.access.ActorContext;
import com.factory.central.cache.LatestTagCache;
import com.factory.central.cache.LatestTagState;
import com.factory.central.mqtt.MqttPublishException;
import com.factory.central.mqtt.MqttPublisher;
import com.factory.central.registry.RegistryLivenessService;
import com.factory.central.registry.TagMapping;
import com.factory.central.stream.CommandUpdateEvent;
import com.factory.central.stream.TelemetryStreamHub;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class CommandService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;
    private final CommandTagResolver tagResolver;
    private final LatestTagCache latestTagCache;
    private final RegistryLivenessService livenessService;
    private final InterlockEvaluator interlockEvaluator;
    private final CommandAuditWriter auditWriter;
    private final CommandAuditLogRepository auditLogRepository;
    private final CommandRejectionRecorder rejectionRecorder;
    private final MqttPublisher mqttPublisher;
    private final TelemetryStreamHub streamHub;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public CommandService(
            CommandTagResolver tagResolver,
            LatestTagCache latestTagCache,
            RegistryLivenessService livenessService,
            InterlockEvaluator interlockEvaluator,
            CommandAuditWriter auditWriter,
            CommandAuditLogRepository auditLogRepository,
            CommandRejectionRecorder rejectionRecorder,
            MqttPublisher mqttPublisher,
            TelemetryStreamHub streamHub,
            ObjectMapper objectMapper,
            Clock clock) {
        this.tagResolver = tagResolver;
        this.latestTagCache = latestTagCache;
        this.livenessService = livenessService;
        this.interlockEvaluator = interlockEvaluator;
        this.auditWriter = auditWriter;
        this.auditLogRepository = auditLogRepository;
        this.rejectionRecorder = rejectionRecorder;
        this.mqttPublisher = mqttPublisher;
        this.streamHub = streamHub;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public CommandAcceptedResponse submit(ActorContext actor, CommandRequest request) {
        Instant now = clock.instant();
        String requestedBy = actor.actorId();
        TagMapping tag;
        try {
            tag = tagResolver.resolve(request);
        } catch (CommandException ex) {
            if (CommandErrorCode.TAG_NOT_FOUND.equals(ex.getCode())) {
                rejectionRecorder.rejectUnknownTagAndThrow(request, requestedBy, now, ex);
            }
            throw ex;
        }
        Optional<LatestTagState> latest =
                latestTagCache.get(tag.getSiteId(), tag.getLineId(), tag.getTagCode());
        String previousValue = latest
                .map(state -> state.value() == null ? null : String.valueOf(state.value()))
                .orElse(null);

        CommandTargetValue.Parsed target;
        try {
            if (!livenessService.isLive(tag)) {
                throw new CommandException(
                        CommandErrorCode.TAG_NOT_ACTIVE,
                        "Tag is not ACTIVE for site/line: " + tag.getTagCode(),
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        null,
                        List.of(new CommandErrorResponse.FieldErrorDetail(
                                "tagCode", "tag, line, and site must all be ACTIVE")));
            }
            target = CommandTargetValue.parse(tag, request.targetValue());
            if (!tag.isWritable()) {
                throw new CommandException(
                        CommandErrorCode.TAG_NOT_WRITABLE,
                        "Tag is not writable: " + tag.getTagCode(),
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        null,
                        List.of(new CommandErrorResponse.FieldErrorDetail("tagCode", "tag is not writable")));
            }
            CommandTargetValue.assertWithinBounds(tag, target.value());
            NodeWriteGuard.check(
                    tag.getDataType(),
                    latest.map(LatestTagState::observedDataType).orElse(null),
                    latest.map(LatestTagState::nodeWritable).orElse(null));
        } catch (CommandException ex) {
            if (ex.getStatus() == HttpStatus.UNPROCESSABLE_ENTITY) {
                rejectionRecorder.rejectAndThrow(
                        tag, request, requestedBy, previousValue, now, CommandStatus.REJECTED_VALIDATION, ex);
            }
            throw ex;
        }

        Optional<String> interlock = interlockEvaluator.evaluate(
                tag.getInterlockRuleKey(), new InterlockContext(tag, target.value(), latestTagCache));
        if (interlock.isPresent()) {
            CommandException ex = new CommandException(
                    CommandErrorCode.INTERLOCK_REJECTED,
                    interlock.get(),
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    null,
                    List.of(new CommandErrorResponse.FieldErrorDetail("interlock", interlock.get())));
            rejectionRecorder.rejectAndThrow(
                    tag, request, requestedBy, previousValue, now, CommandStatus.REJECTED_INTERLOCK, ex);
        }

        UUID commandId = UUID.randomUUID();
        CommandAuditLog audit = auditWriter.insert(new CommandAuditLog(
                commandId,
                tag.getSiteId(),
                tag.getLineId(),
                tag.getTagCode(),
                target.stored(),
                previousValue,
                requestedBy,
                request.reason().trim(),
                CommandStatus.VALIDATED_DISPATCHED,
                null,
                now));

        String topic = "site/" + tag.getSiteId() + "/line/" + tag.getLineId() + "/commands";
        CommandMqttPayload payload = new CommandMqttPayload(
                commandId,
                tag.getSiteId(),
                tag.getLineId(),
                tag.getTagCode(),
                tag.getProtocol().name(),
                tag.getExternalAddress(),
                tag.getDataType().name(),
                target.value(),
                requestedBy,
                now);
        try {
            mqttPublisher.publish(topic, objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            return handlePublishFailure(audit, "MQTT publish failed: payload serialization error");
        } catch (MqttPublishException ex) {
            return handlePublishFailure(audit, "MQTT publish failed: " + rootMessage(ex));
        }

        emit(audit, CommandStatus.VALIDATED_DISPATCHED, null, null, now);
        return new CommandAcceptedResponse(commandId, CommandStatus.VALIDATED_DISPATCHED.asString());
    }

    public List<CommandAuditResponse> listAudit(
            String siteId, String lineId, String tagCode, String status, Integer page, Integer size) {
        CommandStatus parsedStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                parsedStatus = CommandStatus.fromString(status);
            } catch (IllegalArgumentException ex) {
                throw new CommandException(
                        CommandErrorCode.INVALID_PARAMETER,
                        "Unknown status: " + status,
                        HttpStatus.BAD_REQUEST);
            }
        }
        int pageIndex = page == null || page < 0 ? 0 : page;
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return auditLogRepository
                .findFiltered(siteId, lineId, tagCode, parsedStatus, PageRequest.of(pageIndex, pageSize))
                .stream()
                .map(CommandAuditResponse::from)
                .toList();
    }

    private CommandAcceptedResponse handlePublishFailure(CommandAuditLog audit, String reason) {
        Instant now = clock.instant();
        int updated = auditWriter.transitionFromDispatched(
                audit.getCommandId(), CommandStatus.FAILED_EDGE, null, reason, now);
        if (updated == 1) {
            emit(audit, CommandStatus.FAILED_EDGE, null, reason, now);
            throw new CommandException(
                    CommandErrorCode.MQTT_PUBLISH_FAILED,
                    reason,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    audit.getCommandId(),
                    List.of());
        }
        CommandAuditLog current = auditWriter.require(audit.getCommandId());
        if (current.getStatus() == CommandStatus.ACKED) {
            return new CommandAcceptedResponse(current.getCommandId(), CommandStatus.ACKED.asString());
        }
        throw new CommandException(
                CommandErrorCode.MQTT_PUBLISH_FAILED,
                reason,
                HttpStatus.SERVICE_UNAVAILABLE,
                audit.getCommandId(),
                List.of());
    }

    private void emit(
            CommandAuditLog audit, CommandStatus status, Object appliedValue, String errorMessage, Instant at) {
        streamHub.publishCommand(new CommandUpdateEvent(
                audit.getCommandId().toString(),
                audit.getSiteId(),
                audit.getLineId(),
                audit.getTagCode(),
                status.asString(),
                appliedValue,
                errorMessage,
                at));
    }

    private static String rootMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }
}
