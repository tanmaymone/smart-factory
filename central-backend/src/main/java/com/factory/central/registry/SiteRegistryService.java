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

    public SiteRegistryService(SiteRepository siteRepository, RegistryLivenessService livenessService) {
        this.siteRepository = siteRepository;
        this.livenessService = livenessService;
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
                    RegistryLifecycle lifecycle =
                            request.lifecycle() == null ? RegistryLifecycle.DRAFT : request.lifecycle();
                    Site saved = siteRepository.save(new Site(
                            siteCode,
                            request.name().trim(),
                            request.timezone().trim(),
                            SiteStatus.ONLINE,
                            lifecycle));
                    return new SiteCreateResult(SiteResponse.from(saved), true);
                });
    }

    @Transactional
    public SiteResponse update(ActorContext actor, String siteCode, SiteUpdateRequest request) {
        Site existing = requireSite(siteCode.trim());
        boolean wasActive = existing.getLifecycle() == RegistryLifecycle.ACTIVE;
        RegistryLifecycle nextLifecycle =
                request.lifecycle() == null ? existing.getLifecycle() : request.lifecycle();
        existing.apply(request.name().trim(), request.timezone().trim(), nextLifecycle);
        Site saved = siteRepository.save(existing);
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
