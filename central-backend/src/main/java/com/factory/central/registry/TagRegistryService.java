package com.factory.central.registry;

import com.factory.central.cache.LatestTagCache;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagRegistryService {

    private final TagMappingRepository tagMappingRepository;
    private final LineRepository lineRepository;
    private final LatestTagCache latestTagCache;

    public TagRegistryService(
            TagMappingRepository tagMappingRepository,
            LineRepository lineRepository,
            LatestTagCache latestTagCache) {
        this.tagMappingRepository = tagMappingRepository;
        this.lineRepository = lineRepository;
        this.latestTagCache = latestTagCache;
    }

    @Transactional(readOnly = true)
    public List<TagMappingResponse> list(String siteId, String lineId, Protocol protocol, Boolean writable) {
        return tagMappingRepository.findFiltered(siteId, lineId, protocol, writable).stream()
                .map(TagMappingResponse::from)
                .toList();
    }

    @Transactional
    public TagMappingResponse create(TagMappingRequest request) {
        requireLine(request.siteId(), request.lineId());
        requireUniqueTagCode(request.siteId(), request.lineId(), request.tagCode(), null);
        TagBoundsValidator.validate(request);
        TagMapping saved = tagMappingRepository.save(TagMapping.create(UUID.randomUUID(), request));
        return TagMappingResponse.from(saved);
    }

    @Transactional
    public TagMappingResponse update(UUID id, TagMappingRequest request) {
        TagMapping existing = tagMappingRepository
                .findById(id)
                .orElseThrow(() -> new RegistryException(
                        RegistryErrorCode.TAG_NOT_FOUND,
                        "Tag mapping not found: " + id,
                        HttpStatus.NOT_FOUND));

        List<RegistryErrorResponse.FieldErrorDetail> identityErrors = new ArrayList<>();
        if (!existing.getSiteId().equals(request.siteId())) {
            identityErrors.add(new RegistryErrorResponse.FieldErrorDetail(
                    "siteId", "must match existing value " + existing.getSiteId()));
        }
        if (!existing.getLineId().equals(request.lineId())) {
            identityErrors.add(new RegistryErrorResponse.FieldErrorDetail(
                    "lineId", "must match existing value " + existing.getLineId()));
        }
        if (!identityErrors.isEmpty()) {
            throw new RegistryException(
                    RegistryErrorCode.IDENTITY_IMMUTABLE,
                    "siteId and lineId cannot be changed on update",
                    HttpStatus.BAD_REQUEST,
                    identityErrors);
        }

        requireLine(existing.getSiteId(), existing.getLineId());
        requireUniqueTagCode(existing.getSiteId(), existing.getLineId(), request.tagCode(), existing.getId());
        TagBoundsValidator.validate(request);

        existing.apply(request);
        return TagMappingResponse.from(tagMappingRepository.save(existing));
    }

    @Transactional
    public void delete(UUID id) {
        TagMapping existing = tagMappingRepository
                .findById(id)
                .orElseThrow(() -> new RegistryException(
                        RegistryErrorCode.TAG_NOT_FOUND,
                        "Tag mapping not found: " + id,
                        HttpStatus.NOT_FOUND));
        tagMappingRepository.delete(existing);
        latestTagCache.remove(existing.getSiteId(), existing.getLineId(), existing.getTagCode());
    }

    private void requireLine(String siteId, String lineId) {
        if (lineRepository.findById(new LineId(siteId, lineId)).isEmpty()) {
            throw new RegistryException(
                    RegistryErrorCode.LINE_NOT_FOUND,
                    "Line not found: " + siteId + "/" + lineId,
                    HttpStatus.NOT_FOUND);
        }
    }

    private void requireUniqueTagCode(String siteId, String lineId, String tagCode, UUID excludeId) {
        tagMappingRepository
                .findBySiteIdAndLineIdAndTagCode(siteId, lineId, tagCode)
                .ifPresent(found -> {
                    if (excludeId == null || !found.getId().equals(excludeId)) {
                        throw new RegistryException(
                                RegistryErrorCode.TAG_CODE_EXISTS,
                                "Tag code already exists for site/line: " + tagCode,
                                HttpStatus.CONFLICT);
                    }
                });
    }
}
