package com.factory.central.command;

import com.factory.central.cache.LatestTagCache;
import com.factory.central.registry.TagMapping;

public record InterlockContext(TagMapping tag, Object targetValue, LatestTagCache latestTagCache) {}
