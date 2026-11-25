package com.factory.central.registry;

import java.util.List;

public record EdgeConfigLine(String lineId, List<EdgeConfigTag> tags) {}
