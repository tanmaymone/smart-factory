package com.factory.central.registry;

import com.factory.central.access.ActorContext;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SiteRegistryService {

    private final SiteRepository siteRepository;
    private final RegistryLivenessService livenessService;
    private final SiteConfigRevisionService revisionService;

    public SiteRegistryService(
            SiteRepository siteRepository,
            RegistryLivenessService livenessService,
            SiteConfigRevisionService revisionService) {
        this.siteRepository = siteRepository;
        this.livenessService = livenessService;
        this.revisionService = revisionService;
    }

    @Transactional(readOnly = true)
    public List<SiteResponse> list() {
        return siteRepository.findAll().stream().map(SiteResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SiteResponse get(String siteCode) {
        return SiteResponse.from(requireSite(siteCode));
    }

    @Transactional
    public SiteCreateResult create(ActorContext actor, SiteRequest request) {
        String siteCode = request.siteCode().trim();
        return siteRepository
                .findById(siteCode)
                .map(existing -> new SiteCreateResult(SiteResponse.from(existing), false))
                .orElseGet(() -> {
                    var fieldErrors = EntityDisplayLabel.validateCreate(request.siteLabel(), request.lineLabel());
                    if (!fieldErrors.isEmpty()) {
                        throw new RegistryException(
                                RegistryErrorCode.VALIDATION_FAILED,
                                "Request validation failed",
                                HttpStatus.BAD_REQUEST,
                                fieldErrors);
                    }
                    RegistryLifecycle lifecycle =
                            request.lifecycle() == null ? RegistryLifecycle.DRAFT : request.lifecycle();
                    Site saved = siteRepository.save(new Site(
                            siteCode,
                            request.name().trim(),
                            request.timezone().trim(),
                            SiteStatus.ONLINE,
                            lifecycle,
                            request.siteLabel(),
                            request.lineLabel()));
                    revisionService.seedIfAbsent(saved.getId());
                    return new SiteCreateResult(SiteResponse.from(saved), true);
                });
    }

    @Transactional
    public SiteResponse update(ActorContext actor, String siteCode, SiteUpdateRequest request) {
        Site existing = requireSite(siteCode.trim());
        boolean wasActive = existing.getLifecycle() == RegistryLifecycle.ACTIVE;
        RegistryLifecycle nextLifecycle =
                request.lifecycle() == null ? existing.getLifecycle() : request.lifecycle();

        String nextSiteLabel = existing.getSiteLabel();
        String nextLineLabel = existing.getLineLabel();
        if (request.siteLabel() != null) {
            nextSiteLabel = EntityDisplayLabel.normalizeSite(request.siteLabel());
            EntityDisplayLabel.assertValid(nextSiteLabel, "siteLabel");
        }
        if (request.lineLabel() != null) {
            nextLineLabel = EntityDisplayLabel.normalizeLine(request.lineLabel());
            EntityDisplayLabel.assertValid(nextLineLabel, "lineLabel");
        }

        boolean registryFieldsChanged = !existing.getName().equals(request.name().trim())
                || !existing.getTimezone().equals(request.timezone().trim())
                || existing.getLifecycle() != nextLifecycle;

        existing.apply(
                request.name().trim(),
                request.timezone().trim(),
                nextLifecycle,
                request.siteLabel() == null ? null : nextSiteLabel,
                request.lineLabel() == null ? null : nextLineLabel);
        Site saved = siteRepository.save(existing);
        if (registryFieldsChanged) {
            revisionService.bump(saved.getId());
        }
        if (wasActive && saved.getLifecycle() != RegistryLifecycle.ACTIVE) {
            // Parent demotion ⇒ every nested tag is non-live; skip per-tag DB checks.
            livenessService.evictScope(saved.getId(), null);
        }
        return SiteResponse.from(saved);
    }

    private Site requireSite(String siteCode) {
        return siteRepository
                .findById(siteCode)
                .orElseThrow(() -> new RegistryException(
                        RegistryErrorCode.SITE_NOT_FOUND,
                        "Site not found: " + siteCode,
                        HttpStatus.NOT_FOUND));
    }

    public record SiteCreateResult(SiteResponse body, boolean created) {}
}
