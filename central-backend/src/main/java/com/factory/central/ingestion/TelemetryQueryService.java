package com.factory.central.ingestion;

import com.factory.central.cache.LatestTagCache;
import com.factory.central.cache.LatestTagState;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TelemetryQueryService {

    private static final int DEFAULT_LIMIT = 500;
    private static final int MAX_LIMIT = 1000;

    private final LatestTagCache latestTagCache;
    private final TelemetryHistoryRepository historyRepository;
    private final Clock clock;

    public TelemetryQueryService(
            LatestTagCache latestTagCache, TelemetryHistoryRepository historyRepository, Clock clock) {
        this.latestTagCache = latestTagCache;
        this.historyRepository = historyRepository;
        this.clock = clock;
    }

    public List<LatestTagState> latest(String siteId, String lineId, String tagCode) {
        return latestTagCache.find(siteId, lineId, tagCode);
    }

    public List<LatestTagState> alarms(String siteId, String lineId) {
        return latestTagCache.findAlarms(siteId, lineId);
    }

    public List<TelemetryHistoryRow> history(
            String siteId, String lineId, String tagCode, String fromRaw, String toRaw, Integer limitRaw) {
        if (siteId == null
                || siteId.isBlank()
                || lineId == null
                || lineId.isBlank()
                || tagCode == null
                || tagCode.isBlank()) {
            throw new IngestionException(
                    IngestionErrorCode.INVALID_PARAMETER,
                    "siteId, lineId, and tagCode are required",
                    HttpStatus.BAD_REQUEST);
        }

        Optional<Instant> parsedFrom = parseQueryInstant(fromRaw, "from");
        Optional<Instant> parsedTo = parseQueryInstant(toRaw, "to");

        Instant to;
        Instant from;
        if (parsedFrom.isEmpty() && parsedTo.isEmpty()) {
            to = clock.instant();
            from = to.minus(Duration.ofHours(1));
        } else if (parsedFrom.isPresent() && parsedTo.isEmpty()) {
            from = parsedFrom.get();
            to = clock.instant();
        } else if (parsedFrom.isEmpty()) {
            to = parsedTo.get();
            from = to.minus(Duration.ofHours(1));
        } else {
            from = parsedFrom.get();
            to = parsedTo.get();
        }

        if (!from.isBefore(to)) {
            throw new IngestionException(
                    IngestionErrorCode.INVALID_PARAMETER,
                    "from must be before to",
                    HttpStatus.BAD_REQUEST,
                    List.of(new IngestReadingError.FieldErrorDetail("from", "must be before to")));
        }

        int limit = limitRaw == null ? DEFAULT_LIMIT : limitRaw;
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IngestionException(
                    IngestionErrorCode.INVALID_PARAMETER,
                    "limit must be between 1 and " + MAX_LIMIT,
                    HttpStatus.BAD_REQUEST,
                    List.of(new IngestReadingError.FieldErrorDetail(
                            "limit", "must be between 1 and " + MAX_LIMIT)));
        }

        return historyRepository.findHistory(siteId, lineId, tagCode, from, to, limit);
    }

    private Optional<Instant> parseQueryInstant(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        if (TimestampParser.isZoneLessLocalDateTime(raw)) {
            throw new IngestionException(
                    IngestionErrorCode.INVALID_PARAMETER,
                    field + " must include Z or a numeric offset",
                    HttpStatus.BAD_REQUEST,
                    List.of(new IngestReadingError.FieldErrorDetail(
                            field, "must include Z or a numeric offset")));
        }
        return TimestampParser.parseInstant(raw).or(() -> {
            throw new IngestionException(
                    IngestionErrorCode.INVALID_PARAMETER,
                    field + " must be an ISO-8601 timestamp with offset or Z",
                    HttpStatus.BAD_REQUEST,
                    List.of(new IngestReadingError.FieldErrorDetail(
                            field, "must be ISO-8601 with offset or Z")));
        });
    }
}
