package com.factory.central.registry;

import com.factory.central.access.ActorContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Batch import defaults new rows to DRAFT (no live cache write). Explicit
 * {@code lifecycle} on a row can still demote a previously live tag; reconciliation
 * uses {@link RegistryLivenessService} so site/line parents participate in the gate.
 */
@Service
public class TagBatchService {

    private final TagMappingRepository tagMappingRepository;
    private final LineRepository lineRepository;
    private final RegistryLivenessService livenessService;

    public TagBatchService(
            TagMappingRepository tagMappingRepository,
            LineRepository lineRepository,
            RegistryLivenessService livenessService) {
        this.tagMappingRepository = tagMappingRepository;
        this.lineRepository = lineRepository;
        this.livenessService = livenessService;
    }

    @Transactional
    public TagBatchResponse importBatch(ActorContext actor, List<TagBatchRowInput> rows) {
        List<TagBatchRowResult> results = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int failed = 0;
        Map<String, Boolean> priorLive = new HashMap<>();
        Set<String> touchedKeys = new HashSet<>();

        int index = 0;
        for (TagBatchRowInput input : rows) {
            index++;
            TagBatchValidator.ParsedRowHolder holder = new TagBatchValidator.ParsedRowHolder();
            List<TagBatchRowError> errors = new ArrayList<>(TagBatchValidator.validate(input, holder));
            String tagCodeHint = blankToNull(input.get("tagCode"));

            if (!errors.isEmpty() || holder.value == null) {
                failed++;
                results.add(TagBatchRowResult.of(
                        index, input.sourceLine(), tagCodeHint, TagBatchRowOutcome.FAILED, null, errors));
                continue;
            }

            TagBatchValidator.ParsedRow parsed = holder.value;
            if (lineRepository.findById(new LineId(parsed.siteId(), parsed.lineId())).isEmpty()) {
                failed++;
                results.add(TagBatchRowResult.of(
                        index,
                        input.sourceLine(),
                        parsed.tagCode(),
                        TagBatchRowOutcome.FAILED,
                        null,
                        List.of(new TagBatchRowError(
                                "lineId",
                                RegistryErrorCode.LINE_NOT_FOUND,
                                "Line not found: " + parsed.siteId() + "/" + parsed.lineId()))));
                continue;
            }

            String key = key(parsed.siteId(), parsed.lineId(), parsed.tagCode());
            if (!priorLive.containsKey(key)) {
                priorLive.put(key, livenessService.isLive(parsed.siteId(), parsed.lineId(), parsed.tagCode()));
            }

            try {
                var existingOpt = tagMappingRepository.findBySiteIdAndLineIdAndTagCode(
                        parsed.siteId(), parsed.lineId(), parsed.tagCode());
                if (existingOpt.isPresent()) {
                    TagMapping existing = existingOpt.get();
                    RegistryLifecycle lifecycle =
                            parsed.lifecycleExplicit() ? parsed.lifecycle() : null;
                    existing.applyBatch(
                            parsed.description(),
                            parsed.protocol(),
                            parsed.endpointUrl(),
                            parsed.externalAddress(),
                            parsed.dataType(),
                            parsed.unit(),
                            parsed.writable(),
                            parsed.minValue(),
                            parsed.maxValue(),
                            parsed.deadband(),
                            parsed.warningLow(),
                            parsed.warningHigh(),
                            parsed.criticalLow(),
                            parsed.criticalHigh(),
                            parsed.interlockRuleKey(),
                            lifecycle);
                    TagMapping saved = tagMappingRepository.save(existing);
                    updated++;
                    touchedKeys.add(key);
                    results.add(TagBatchRowResult.of(
                            index,
                            input.sourceLine(),
                            saved.getTagCode(),
                            TagBatchRowOutcome.UPDATED,
                            saved.getId(),
                            List.of()));
                } else {
                    RegistryLifecycle lifecycle = parsed.lifecycleExplicit()
                            ? parsed.lifecycle()
                            : RegistryLifecycle.DRAFT;
                    TagMapping createdTag = new TagMapping(
                            UUID.randomUUID(),
                            parsed.siteId(),
                            parsed.lineId(),
                            parsed.tagCode(),
                            parsed.description(),
                            parsed.protocol(),
                            parsed.endpointUrl(),
                            parsed.externalAddress(),
                            parsed.dataType(),
                            parsed.unit(),
                            parsed.writable(),
                            parsed.minValue(),
                            parsed.maxValue(),
                            parsed.deadband(),
                            parsed.warningLow(),
                            parsed.warningHigh(),
                            parsed.criticalLow(),
                            parsed.criticalHigh(),
                            parsed.interlockRuleKey(),
                            lifecycle);
                    TagMapping saved = tagMappingRepository.save(createdTag);
                    created++;
                    touchedKeys.add(key);
                    results.add(TagBatchRowResult.of(
                            index,
                            input.sourceLine(),
                            saved.getTagCode(),
                            TagBatchRowOutcome.CREATED,
                            saved.getId(),
                            List.of()));
                }
            } catch (DataIntegrityViolationException ex) {
                failed++;
                results.add(TagBatchRowResult.of(
                        index,
                        input.sourceLine(),
                        parsed.tagCode(),
                        TagBatchRowOutcome.FAILED,
                        null,
                        List.of(new TagBatchRowError(
                                "tagCode",
                                RegistryErrorCode.TAG_CODE_EXISTS,
                                "Tag code already exists for site/line"))));
            }
        }

        // Cache policy: batch never seeds latest-state. Demotion clears; promotion to
        // live (tag+line+site ACTIVE) leaves cache empty so the next ingest sample fills it.
        for (String key : touchedKeys) {
            String[] parts = key.split("\\|", 3);
            boolean before = Boolean.TRUE.equals(priorLive.get(key));
            boolean after = livenessService.isLive(parts[0], parts[1], parts[2]);
            if (!after) {
                if (before) {
                    livenessService.removeCacheEntry(parts[0], parts[1], parts[2]);
                } else {
                    livenessService.reconcileCache(parts[0], parts[1], parts[2]);
                }
            }
        }

        return new TagBatchResponse(rows.size(), created, updated, failed, results);
    }

    public TagBatchResponse importCsv(ActorContext actor, String body) {
        TagBatchCsvParser.ParseResult parsed = TagBatchCsvParser.parse(body);
        if (!parsed.ok()) {
            throw new RegistryException(
                    RegistryErrorCode.VALIDATION_FAILED,
                    parsed.message(),
                    HttpStatus.BAD_REQUEST,
                    parsed.errors());
        }
        return importBatch(actor, parsed.rows());
    }

    private static String key(String siteId, String lineId, String tagCode) {
        return siteId + '|' + lineId + '|' + tagCode;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
