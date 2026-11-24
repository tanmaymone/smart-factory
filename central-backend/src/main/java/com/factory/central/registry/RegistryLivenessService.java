package com.factory.central.registry;

import com.factory.central.cache.LatestTagCache;
import com.factory.central.cache.LatestTagState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single gate for tag liveness (site + line + tag all ACTIVE) and for removing
 * cache entries that are no longer live after registry mutations.
 */
@Service
public class RegistryLivenessService {

    private final SiteRepository siteRepository;
    private final LineRepository lineRepository;
    private final TagMappingRepository tagMappingRepository;
    private final LatestTagCache latestTagCache;

    public RegistryLivenessService(
            SiteRepository siteRepository,
            LineRepository lineRepository,
            TagMappingRepository tagMappingRepository,
            LatestTagCache latestTagCache) {
        this.siteRepository = siteRepository;
        this.lineRepository = lineRepository;
        this.tagMappingRepository = tagMappingRepository;
        this.latestTagCache = latestTagCache;
    }

    @Transactional(readOnly = true)
    public boolean isLive(TagMapping tag) {
        if (tag == null) {
            return false;
        }
        Site site = siteRepository.findById(tag.getSiteId()).orElse(null);
        Line line = lineRepository.findById(new LineId(tag.getSiteId(), tag.getLineId())).orElse(null);
        return RegistryLiveness.isLive(site, line, tag);
    }

    @Transactional(readOnly = true)
    public boolean isLive(String siteId, String lineId, String tagCode) {
        Optional<TagMapping> tag =
                tagMappingRepository.findBySiteIdAndLineIdAndTagCode(siteId, lineId, tagCode);
        return tag.filter(this::isLive).isPresent();
    }

    @Transactional(readOnly = true)
    public List<LatestTagState> filterLive(List<LatestTagState> states) {
        if (states == null || states.isEmpty()) {
            return List.of();
        }
        Map<String, Site> sites = new HashMap<>();
        Map<String, Line> lines = new HashMap<>();
        Map<String, TagMapping> tags = new HashMap<>();
        List<LatestTagState> live = new ArrayList<>();
        for (LatestTagState state : states) {
            Site site = sites.computeIfAbsent(
                    state.siteId(), id -> siteRepository.findById(id).orElse(null));
            String lineKey = state.siteId() + '|' + state.lineId();
            Line line = lines.computeIfAbsent(
                    lineKey,
                    key -> lineRepository
                            .findById(new LineId(state.siteId(), state.lineId()))
                            .orElse(null));
            String tagKey = lineKey + '|' + state.tagCode();
            TagMapping tag = tags.computeIfAbsent(
                    tagKey,
                    key -> tagMappingRepository
                            .findBySiteIdAndLineIdAndTagCode(state.siteId(), state.lineId(), state.tagCode())
                            .orElse(null));
            if (RegistryLiveness.isLive(site, line, tag)) {
                live.add(state);
            }
        }
        return live;
    }

    /**
     * Evict every cached point in scope whose current site/line/tag hierarchy is
     * not live. Scope nulls match {@link LatestTagCache#find}. Prefer
     * {@link #evictScope} after a parent demotion — that path is O(cached) with no
     * per-tag DB lookups, because {@link RegistryLiveness} already proves every
     * child is non-live once the site or line leaves ACTIVE.
     */
    @Transactional(readOnly = true)
    public void reconcileCache(String siteId, String lineId, String tagCode) {
        for (LatestTagState state : latestTagCache.find(siteId, lineId, tagCode)) {
            if (!isLive(state.siteId(), state.lineId(), state.tagCode())) {
                latestTagCache.remove(state.siteId(), state.lineId(), state.tagCode());
            }
        }
    }

    /**
     * Unconditional eviction of every cached point under a site (and optional line).
     * Call only when that parent left ACTIVE; hierarchy makes every nested tag non-live.
     */
    public void evictScope(String siteId, String lineId) {
        for (LatestTagState state : latestTagCache.find(siteId, lineId, null)) {
            latestTagCache.remove(state.siteId(), state.lineId(), state.tagCode());
        }
    }

    public void removeCacheEntry(String siteId, String lineId, String tagCode) {
        latestTagCache.remove(siteId, lineId, tagCode);
    }
}
