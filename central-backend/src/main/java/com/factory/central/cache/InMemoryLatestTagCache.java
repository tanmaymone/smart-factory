package com.factory.central.cache;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryLatestTagCache implements LatestTagCache {

    private final ConcurrentMap<String, LatestTagState> states = new ConcurrentHashMap<>();

    @Override
    public void put(LatestTagState state) {
        states.put(key(state.siteId(), state.lineId(), state.tagCode()), state);
    }

    @Override
    public void remove(String siteId, String lineId, String tagCode) {
        states.remove(key(siteId, lineId, tagCode));
    }

    @Override
    public Optional<LatestTagState> get(String siteId, String lineId, String tagCode) {
        return Optional.ofNullable(states.get(key(siteId, lineId, tagCode)));
    }

    @Override
    public List<LatestTagState> find(String siteId, String lineId, String tagCode) {
        List<LatestTagState> matches = new ArrayList<>();
        for (LatestTagState state : states.values()) {
            if (matchesFilter(state, siteId, lineId, tagCode)) {
                matches.add(state);
            }
        }
        matches.sort((a, b) -> {
            int site = a.siteId().compareTo(b.siteId());
            if (site != 0) {
                return site;
            }
            int line = a.lineId().compareTo(b.lineId());
            if (line != 0) {
                return line;
            }
            return a.tagCode().compareTo(b.tagCode());
        });
        return matches;
    }

    @Override
    public List<LatestTagState> findAlarms(String siteId, String lineId) {
        return find(siteId, lineId, null).stream()
                .filter(state -> state.severity() != AlarmSeverity.NONE)
                .toList();
    }

    private static boolean matchesFilter(LatestTagState state, String siteId, String lineId, String tagCode) {
        if (siteId != null && !siteId.equals(state.siteId())) {
            return false;
        }
        if (lineId != null && !lineId.equals(state.lineId())) {
            return false;
        }
        return tagCode == null || tagCode.equals(state.tagCode());
    }

    static String key(String siteId, String lineId, String tagCode) {
        return siteId + '|' + lineId + '|' + tagCode;
    }
}
