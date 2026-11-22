package com.factory.central.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.factory.central.cache.LatestTagState;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/telemetry")
public class TelemetryController {

    private final TelemetryIngestService ingestService;
    private final TelemetryQueryService queryService;

    public TelemetryController(TelemetryIngestService ingestService, TelemetryQueryService queryService) {
        this.ingestService = ingestService;
        this.queryService = queryService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<IngestResponse> ingest(@RequestBody JsonNode body) {
        IngestResult result = ingestService.ingestHttp(body);
        if (result.bufferFull()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(IngestResponse.bufferFull());
        }
        if (result.accepted() >= 1) {
            return ResponseEntity.accepted().body(IngestResponse.accepted(result.accepted(), result.errors()));
        }
        String code = resolveRejectedCode(result.errors());
        return ResponseEntity.unprocessableEntity()
                .body(IngestResponse.rejected(code, "All readings were rejected", result.errors()));
    }

    @GetMapping("/latest")
    public List<LatestTagStateResponse> latest(
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String lineId,
            @RequestParam(required = false) String tagCode) {
        return queryService.latest(siteId, lineId, tagCode).stream()
                .map(LatestTagStateResponse::from)
                .toList();
    }

    @GetMapping("/history")
    public List<TelemetryHistoryResponse> history(
            @RequestParam String siteId,
            @RequestParam String lineId,
            @RequestParam String tagCode,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Integer limit) {
        return queryService.history(siteId, lineId, tagCode, from, to, limit).stream()
                .map(TelemetryHistoryResponse::from)
                .toList();
    }

    @GetMapping("/alarms")
    public List<LatestTagStateResponse> alarms(
            @RequestParam(required = false) String siteId, @RequestParam(required = false) String lineId) {
        List<LatestTagState> alarms = queryService.alarms(siteId, lineId);
        return alarms.stream().map(LatestTagStateResponse::from).toList();
    }

    static String resolveRejectedCode(List<IngestReadingError> errors) {
        if (errors == null || errors.isEmpty()) {
            return IngestionErrorCode.VALIDATION_FAILED;
        }
        Set<String> codes = new HashSet<>();
        for (IngestReadingError error : errors) {
            codes.add(error.code());
        }
        if (codes.size() == 1) {
            return codes.iterator().next();
        }
        return IngestionErrorCode.INGEST_REJECTED;
    }
}
