package com.factory.central.command;

import com.factory.central.cache.LatestTagState;
import com.factory.central.registry.TagMapping;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Rejects a write when the same site/line {@code VALVE_101_POS} is missing, not exactly {@code GOOD},
 * non-numeric, non-finite, or numerically below {@code 5}.
 */
@Component
public class InletValveOpenInterlockRule implements InterlockRule {

    public static final String KEY = "INLET_VALVE_OPEN";
    public static final String PREREQUISITE_TAG = "VALVE_101_POS";
    public static final double MIN_OPEN = 5.0;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Optional<String> violation(InterlockContext context) {
        TagMapping tag = context.tag();
        Optional<LatestTagState> sample =
                context.latestTagCache().get(tag.getSiteId(), tag.getLineId(), PREREQUISITE_TAG);
        if (sample.isEmpty()) {
            return Optional.of(PREREQUISITE_TAG + " sample is missing");
        }
        LatestTagState state = sample.get();
        if (state.timestamp() == null) {
            return Optional.of(PREREQUISITE_TAG + " timestamp is missing");
        }
        String quality = state.quality();
        if (quality == null || !"GOOD".equals(quality)) {
            return Optional.of(PREREQUISITE_TAG + " quality is not GOOD (quality=" + quality + ")");
        }
        Object value = state.value();
        if (!(value instanceof Number number)) {
            return Optional.of(PREREQUISITE_TAG + " value is not numeric");
        }
        double numeric = number.doubleValue();
        if (!Double.isFinite(numeric)) {
            return Optional.of(PREREQUISITE_TAG + " value is not finite");
        }
        if (numeric < MIN_OPEN) {
            return Optional.of(PREREQUISITE_TAG + " must be >= " + MIN_OPEN + " (was " + numeric + ")");
        }
        return Optional.empty();
    }
}
