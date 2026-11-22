package com.factory.central.cache;

import java.util.List;
import java.util.Optional;

public interface LatestTagCache {

    void put(LatestTagState state);

    void remove(String siteId, String lineId, String tagCode);

    Optional<LatestTagState> get(String siteId, String lineId, String tagCode);

    List<LatestTagState> find(String siteId, String lineId, String tagCode);

    List<LatestTagState> findAlarms(String siteId, String lineId);
}
