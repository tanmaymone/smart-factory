package com.factory.central.access;

import java.util.Optional;

public record AccessRouteMatch(AccessAction action, Optional<String> targetSiteId) {}