package com.factory.central.registry;

import com.factory.central.access.ActorContext;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LineRegistryService {

    private final LineRepository lineRepository;
    private final SiteRepository siteRepository;
    private final RegistryLivenessService livenessService;
    private final SiteConfigRevisionService revisionService;

    public LineRegistryService(
            LineRepository lineRepository,
            SiteRepository siteRepository,
            RegistryLivenessService livenessService,
            SiteConfigRevisionService revisionService) {
        this.lineRepository = lineRepository;
        this.siteRepository = siteRepository;
        this.livenessService = livenessService;
        this.revisionService = revisionService;
    }

    @Transactional(readOnly = true)
    public List<LineResponse> list(String siteCode) {
        if (siteCode == null || siteCode.isBlank()) {
            return lineRepository.findAll().stream().map(LineResponse::from).toList();
        }
        return lineRepository.findBySiteId(siteCode.trim()).stream().map(LineResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public LineResponse get(String siteCode, String lineCode) {
        return LineResponse.from(requireLine(siteCode.trim(), lineCode.trim()));
    }

    @Transactional
    public LineCreateResult create(ActorContext actor, LineRequest request) {
        String siteCode = request.siteCode().trim();
        String lineCode = request.lineCode().trim();
        requireSite(siteCode);
        LineId id = new LineId(siteCode, lineCode);
        return lineRepository
                .findById(id)
                .map(existing -> new LineCreateResult(LineResponse.from(existing), false))
                .orElseGet(() -> {
                    RegistryLifecycle lifecycle =
                            request.lifecycle() == null ? RegistryLifecycle.DRAFT : request.lifecycle();
                    Line saved = lineRepository.save(
                            new Line(siteCode, lineCode, request.name().trim(), lifecycle));
                    revisionService.bump(saved.getSiteId());
                    return new LineCreateResult(LineResponse.from(saved), true);
                });
    }

    @Transactional
    public LineResponse update(ActorContext actor, String siteCode, String lineCode, LineUpdateRequest request) {
        Line existing = requireLine(siteCode.trim(), lineCode.trim());
        boolean wasActive = existing.getLifecycle() == RegistryLifecycle.ACTIVE;
        RegistryLifecycle nextLifecycle =
                request.lifecycle() == null ? existing.getLifecycle() : request.lifecycle();
        existing.apply(request.name().trim(), nextLifecycle);
        Line saved = lineRepository.save(existing);
        revisionService.bump(saved.getSiteId());
        if (wasActive && saved.getLifecycle() != RegistryLifecycle.ACTIVE) {
            // Parent demotion ⇒ every nested tag on this line is non-live; skip per-tag DB checks.
            livenessService.evictScope(saved.getSiteId(), saved.getLineId());
        }
        return LineResponse.from(saved);
    }

    private void requireSite(String siteCode) {
        if (siteRepository.findById(siteCode).isEmpty()) {
            throw new RegistryException(
                    RegistryErrorCode.SITE_NOT_FOUND, "Site not found: " + siteCode, HttpStatus.NOT_FOUND);
        }
    }

    private Line requireLine(String siteCode, String lineCode) {
        return lineRepository
                .findById(new LineId(siteCode, lineCode))
                .orElseThrow(() -> new RegistryException(
                        RegistryErrorCode.LINE_NOT_FOUND,
                        "Line not found: " + siteCode + "/" + lineCode,
                        HttpStatus.NOT_FOUND));
    }

    public record LineCreateResult(LineResponse body, boolean created) {}
}
