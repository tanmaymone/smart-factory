package com.factory.central.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.factory.central.cache.AlarmSeverity;
import com.factory.central.cache.LatestTagCache;
import com.factory.central.cache.LatestTagState;
import com.factory.central.cache.TelemetryStreamSink;
import com.factory.central.ingestion.TelemetryBatchWriter.EnqueueOutcome;
import com.factory.central.ingestion.TelemetryPayloadParser.ParsedPayload;
import com.factory.central.ingestion.TelemetryPayloadParser.PayloadParseException;
import com.factory.central.ingestion.TelemetryPayloadParser.RawReading;
import com.factory.central.ingestion.TelemetryPayloadParser.ReadingRejectException;
import com.factory.central.ingestion.ValueCoercer.CoercedValue;
import com.factory.central.registry.TagMapping;
import com.factory.central.registry.TagMappingRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class TelemetryIngestService {

    private final TagMappingRepository tagMappingRepository;
    private final AlarmEvaluator alarmEvaluator;
    private final LatestTagCache latestTagCache;
    private final TelemetryBatchWriter batchWriter;
    private final TelemetryStreamSink streamSink;
    private final Clock clock;

    public TelemetryIngestService(
            TagMappingRepository tagMappingRepository,
            AlarmEvaluator alarmEvaluator,
            LatestTagCache latestTagCache,
            TelemetryBatchWriter batchWriter,
            TelemetryStreamSink streamSink,
            Clock clock) {
        this.tagMappingRepository = tagMappingRepository;
        this.alarmEvaluator = alarmEvaluator;
        this.latestTagCache = latestTagCache;
        this.batchWriter = batchWriter;
        this.streamSink = streamSink;
        this.clock = clock;
    }

    public IngestResult ingestHttp(JsonNode body) {
        return ingest(body, null, null, IngestSource.HTTP);
    }

    public IngestResult ingestMqtt(JsonNode body, String topicSiteId, String topicLineId) {
        return ingest(body, topicSiteId, topicLineId, IngestSource.MQTT);
    }

    private IngestResult ingest(JsonNode body, String topicSiteId, String topicLineId, IngestSource source) {
        ParsedPayload payload;
        try {
            payload = TelemetryPayloadParser.parse(body);
        } catch (PayloadParseException ex) {
            return IngestResult.of(
                    0,
                    List.of(IngestReadingError.of(0, null, null, ex.getCode(), ex.getMessage())));
        }

        if (topicSiteId != null
                && topicLineId != null
                && (!topicSiteId.equals(payload.siteId()) || !topicLineId.equals(payload.lineId()))) {
            List<IngestReadingError> errors = new ArrayList<>();
            if (payload.readings().isEmpty()) {
                errors.add(IngestReadingError.of(
                        0,
                        null,
                        null,
                        IngestionErrorCode.TOPIC_PAYLOAD_MISMATCH,
                        "MQTT topic site/line does not match payload"));
            } else {
                for (RawReading reading : payload.readings()) {
                    errors.add(IngestReadingError.of(
                            reading.index(),
                            reading.tagCode(),
                            reading.externalAddress(),
                            IngestionErrorCode.TOPIC_PAYLOAD_MISMATCH,
                            "MQTT topic site/line does not match payload"));
                }
            }
            return IngestResult.of(0, errors);
        }

        Instant serverNow = clock.instant();
        List<AcceptedReading> accepted = new ArrayList<>();
        List<IngestReadingError> errors = new ArrayList<>();

        if (payload.readings().isEmpty()) {
            errors.add(IngestReadingError.of(
                    0, null, null, IngestionErrorCode.VALIDATION_FAILED, "readings must not be empty"));
            return IngestResult.of(0, errors);
        }

        for (RawReading reading : payload.readings()) {
            try {
                accepted.add(resolveReading(payload, reading, serverNow));
            } catch (ReadingRejectException ex) {
                errors.add(IngestReadingError.of(
                        reading.index(),
                        reading.tagCode(),
                        reading.externalAddress(),
                        ex.getCode(),
                        ex.getMessage(),
                        ex.getFieldErrors()));
            }
        }

        if (accepted.isEmpty()) {
            return IngestResult.of(0, errors);
        }

        List<TelemetryHistoryRow> rows = accepted.stream().map(AcceptedReading::historyRow).toList();
        EnqueueOutcome outcome =
                source == IngestSource.HTTP
                        ? batchWriter.enqueueHttpAndFlush(rows)
                        : batchWriter.enqueueMqtt(rows, payload.siteId(), payload.lineId(), payload.messageId());

        if (outcome == EnqueueOutcome.BUFFER_FULL) {
            return IngestResult.bufferFull(errors);
        }

        for (AcceptedReading reading : accepted) {
            updateCache(reading);
        }
        return IngestResult.of(accepted.size(), errors);
    }

    private AcceptedReading resolveReading(ParsedPayload payload, RawReading reading, Instant serverNow) {
        TagMapping tag = resolveTag(payload.siteId(), payload.lineId(), reading);
        Instant timestamp = TelemetryPayloadParser.resolveReadingTimestamp(reading, payload.envelopeTimestamp(), serverNow);
        CoercedValue coerced = ValueCoercer.coerce(tag, reading.value());
        String quality = normalizeQuality(reading.quality());
        TelemetryHistoryRow row = new TelemetryHistoryRow(
                timestamp,
                tag.getSiteId(),
                tag.getLineId(),
                tag.getTagCode(),
                coerced.numericValue(),
                coerced.booleanValue(),
                coerced.stringValue(),
                quality);
        return new AcceptedReading(
                tag,
                coerced.value(),
                quality,
                timestamp,
                row,
                TelemetryPayloadParser.observedDataType(reading.dataType()),
                reading.nodeWritable());
    }

    private TagMapping resolveTag(String siteId, String lineId, RawReading reading) {
        if (reading.tagCode() != null && !reading.tagCode().isBlank()) {
            return tagMappingRepository
                    .findBySiteIdAndLineIdAndTagCode(siteId, lineId, reading.tagCode())
                    .orElseThrow(() -> new ReadingRejectException(
                            IngestionErrorCode.TAG_NOT_FOUND,
                            "Tag not found: " + reading.tagCode(),
                            List.of()));
        }
        if (reading.externalAddress() == null || reading.externalAddress().isBlank()) {
            throw new ReadingRejectException(
                    IngestionErrorCode.VALIDATION_FAILED,
                    "tagCode or externalAddress is required",
                    List.of(new IngestReadingError.FieldErrorDetail(
                            "tagCode", "tagCode or externalAddress is required")));
        }
        List<TagMapping> matches = tagMappingRepository.findBySiteIdAndLineIdAndExternalAddress(
                siteId, lineId, reading.externalAddress());
        if (matches.isEmpty()) {
            throw new ReadingRejectException(
                    IngestionErrorCode.TAG_NOT_FOUND,
                    "Tag not found for externalAddress: " + reading.externalAddress(),
                    List.of());
        }
        if (matches.size() > 1) {
            throw new ReadingRejectException(
                    IngestionErrorCode.AMBIGUOUS_ADDRESS,
                    "Multiple tags share externalAddress: " + reading.externalAddress(),
                    List.of());
        }
        return matches.getFirst();
    }

    private void updateCache(AcceptedReading reading) {
        TagMapping tag = reading.tag();
        Optional<LatestTagState> existing =
                latestTagCache.get(tag.getSiteId(), tag.getLineId(), tag.getTagCode());

        if (AlarmEvaluator.isFrozenQuality(reading.quality())) {
            LatestTagState prior = existing.orElseGet(() -> LatestTagState.initial(
                    tag.getSiteId(),
                    tag.getLineId(),
                    tag.getTagCode(),
                    reading.value(),
                    reading.quality(),
                    reading.timestamp(),
                    AlarmSeverity.NONE,
                    null));
            boolean freeze = prior.severity() != AlarmSeverity.NONE;
            LatestTagState updated = prior.withSample(
                    reading.value(),
                    reading.quality(),
                    reading.timestamp(),
                    reading.observedDataType(),
                    reading.nodeWritable())
                    .withAlarm(
                            prior.severity(),
                            prior.direction(),
                            freeze,
                            prior.alarmValue(),
                            prior.alarmTimestamp());
            latestTagCache.put(updated);
            publishLive(existing.orElse(null), updated);
            return;
        }

        AlarmEvaluation evaluation = alarmEvaluator.evaluate(tag, reading.value(), reading.quality());
        String observed = reading.observedDataType();
        Boolean nodeWritable = reading.nodeWritable();
        if (existing.isPresent()) {
            if (observed == null) {
                observed = existing.get().observedDataType();
            }
            if (nodeWritable == null) {
                nodeWritable = existing.get().nodeWritable();
            }
        }
        LatestTagState next = LatestTagState.initial(
                tag.getSiteId(),
                tag.getLineId(),
                tag.getTagCode(),
                reading.value(),
                reading.quality(),
                reading.timestamp(),
                evaluation.severity(),
                evaluation.direction(),
                observed,
                nodeWritable);
        latestTagCache.put(next);
        publishLive(existing.orElse(null), next);
    }

    private void publishLive(LatestTagState prior, LatestTagState next) {
        streamSink.publishTag(next);
        if (alarmTransitioned(prior, next)) {
            streamSink.publishAlarm(next);
        }
    }

    static boolean alarmTransitioned(LatestTagState prior, LatestTagState next) {
        if (prior == null) {
            return next.severity() != AlarmSeverity.NONE;
        }
        return prior.severity() != next.severity()
                || !Objects.equals(prior.direction(), next.direction())
                || prior.alarmFrozen() != next.alarmFrozen();
    }

    private static String normalizeQuality(String quality) {
        if (quality == null || quality.isBlank()) {
            // Persist blank as non-good; never invent GOOD for a missing quality.
            return "";
        }
        return quality.trim().toUpperCase();
    }

    private enum IngestSource {
        HTTP,
        MQTT
    }
}
