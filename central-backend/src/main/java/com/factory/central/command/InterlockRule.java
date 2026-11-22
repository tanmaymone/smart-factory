package com.factory.central.command;

import java.util.Optional;

public interface InterlockRule {

    String key();

    /**
     * @return empty when the write is allowed; otherwise a human-readable rejection reason
     */
    Optional<String> violation(InterlockContext context);
}
