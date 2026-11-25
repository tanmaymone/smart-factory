package com.factory.edge.filter;

import com.factory.edge.opcua.OpcUaProperties;
import com.factory.edge.opcua.QualityGate;
import com.factory.edge.opcua.TagReading;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-tag change-of-value / deadband filter. Keyed by {@code siteId|lineId|tagCode}.
 */
public final class DeadbandFilter {

    private static final Logger log = LoggerFactory.getLogger(DeadbandFilter.class);

    private volatile Map<String, BigDecimal> deadbands;
    private final ConcurrentHashMap<String, TagState> states = new ConcurrentHashMap<>();

    public DeadbandFilter(List<OpcUaProperties.TagConfig> tags) {
        this.deadbands = bandsOf(tags);
    }

    /** Builds the {@code siteId|lineId|tagCode} to band map, rejecting negative bands. */
    public static Map<String, BigDecimal> bandsOf(List<OpcUaProperties.TagConfig> tags) {
        Map<String, BigDecimal> bands = new HashMap<>();
        if (tags != null) {
            for (OpcUaProperties.TagConfig tag : tags) {
                double band = tag.getDeadband();
                if (band < 0.0) {
                    throw new IllegalArgumentException(
                            "deadband must be >= 0 for tagCode="
                                    + tag.getTagCode()
                                    + " siteId="
                                    + tag.getSiteId()
                                    + " lineId="
                                    + tag.getLineId()
                                    + " but was "
                                    + band);
                }
                bands.put(key(tag.getSiteId(), tag.getLineId(), tag.getTagCode()), BigDecimal.valueOf(band));
            }
        }
        return Map.copyOf(bands);
    }

    /** Swaps the band table. Per-tag state survives so unchanged tags keep their baseline. */
    public void replaceBands(Map<String, BigDecimal> bands) {
        this.deadbands = bands == null ? Map.of() : Map.copyOf(bands);
    }

    /** Drops baselines for new or re-addressed tags so their next reading always emits. */
    public void evict(Collection<String> keys) {
        if (keys == null) {
            return;
        }
        keys.forEach(states::remove);
    }

    /** Drops every baseline — used when the whole tag set is replaced or the edge goes IDLE. */
    public void clearAll() {
        states.clear();
    }

    /**
     * @return {@code true} when the reading should be published / queued
     */
    public boolean shouldEmit(TagReading reading) {
        String tagKey = key(reading.siteId(), reading.lineId(), reading.tagCode());
        BigDecimal band = deadbands.getOrDefault(tagKey, BigDecimal.ZERO);

        TagState previous = states.get(tagKey);
        if (previous == null) {
            states.put(tagKey, TagState.fromFirst(reading));
            return true;
        }

        boolean qualityOrStatusChanged =
                !Objects.equals(previous.quality, reading.quality())
                        || !Objects.equals(previous.statusName, reading.statusName());

        String quality = reading.quality();
        if ("BAD".equals(quality)) {
            if (qualityOrStatusChanged) {
                states.put(tagKey, previous.afterEmit(reading));
                return true;
            }
            log.debug("Deadband hold BAD repeat key={}", tagKey);
            return false;
        }

        if ("UNCERTAIN".equals(quality)) {
            if (qualityOrStatusChanged
                    && (!"UNCERTAIN".equals(previous.quality)
                            || !Objects.equals(previous.statusName, reading.statusName()))) {
                // Entering UNCERTAIN or status change always emits
                states.put(tagKey, previous.afterUncertainEmit(reading));
                return true;
            }
            if (shouldHoldValue(previous.lastEmittedValue, reading.value(), band)) {
                log.debug("Deadband hold UNCERTAIN key={} band={}", tagKey, band);
                return false;
            }
            states.put(tagKey, previous.afterUncertainEmit(reading));
            return true;
        }

        // GOOD — including return from BAD/UNCERTAIN: deadband against lastGood when present
        Object baseline = previous.lastGoodValue;
        if (baseline == null) {
            if (isNonFinite(reading.value())
                    && previous.lastEmittedValue != null
                    && sameNonFiniteKind(previous.lastEmittedValue, reading.value())) {
                states.put(tagKey, previous.withQuality(reading));
                log.debug("Deadband hold non-finite repeat key={}", tagKey);
                return false;
            }
            states.put(tagKey, previous.afterEmit(reading));
            return true;
        }

        if (isNonFinite(reading.value())) {
            states.put(tagKey, previous.afterNonFiniteGood(reading));
            return true;
        }

        if (shouldHoldValue(baseline, reading.value(), band)) {
            states.put(tagKey, previous.withQuality(reading));
            log.debug("Deadband hold GOOD key={} band={}", tagKey, band);
            return false;
        }

        states.put(tagKey, previous.afterEmit(reading));
        return true;
    }

    static boolean shouldHoldValue(Object last, Object next, BigDecimal band) {
        if (last == null && next == null) {
            return true;
        }
        if (last == null || next == null) {
            return false;
        }
        if (isNonFinite(last) || isNonFinite(next)) {
            return sameNonFiniteKind(last, next);
        }
        if (last instanceof Boolean || next instanceof Boolean || last instanceof String || next instanceof String) {
            return Objects.equals(last, next);
        }
        if (!(last instanceof Number lastNum) || !(next instanceof Number nextNum)) {
            return Objects.equals(last, next);
        }
        NumberKind lastKind = kindOf(lastNum);
        NumberKind nextKind = kindOf(nextNum);
        if (lastKind == NumberKind.OTHER || nextKind == NumberKind.OTHER || lastKind != nextKind) {
            return false;
        }
        BigDecimal delta = toDecimal(lastNum).subtract(toDecimal(nextNum)).abs();
        // Exact equality always holds (incl. deadband 0 and 0.0 vs -0.0).
        // Otherwise hold strictly inside the band; emit at exact band and beyond.
        return delta.signum() == 0 || delta.compareTo(band) < 0;
    }

    static BigDecimal toDecimal(Number number) {
        if (number instanceof Float f) {
            return BigDecimal.valueOf(f.doubleValue());
        }
        if (number instanceof Double d) {
            return BigDecimal.valueOf(d);
        }
        return BigDecimal.valueOf(number.longValue());
    }

    static boolean isNonFinite(Object value) {
        if (value instanceof Double d) {
            return !Double.isFinite(d);
        }
        if (value instanceof Float f) {
            return !Float.isFinite(f);
        }
        return false;
    }

    static boolean sameNonFiniteKind(Object a, Object b) {
        return nonFiniteKind(a) == nonFiniteKind(b) && nonFiniteKind(a) != NonFiniteKind.FINITE;
    }

    private static NonFiniteKind nonFiniteKind(Object value) {
        double d;
        if (value instanceof Double dbl) {
            d = dbl;
        } else if (value instanceof Float f) {
            d = f.doubleValue();
        } else {
            return NonFiniteKind.FINITE;
        }
        if (Double.isNaN(d)) {
            return NonFiniteKind.NAN;
        }
        if (d == Double.POSITIVE_INFINITY) {
            return NonFiniteKind.POS_INF;
        }
        if (d == Double.NEGATIVE_INFINITY) {
            return NonFiniteKind.NEG_INF;
        }
        return NonFiniteKind.FINITE;
    }

    private static NumberKind kindOf(Number number) {
        if (number instanceof Byte
                || number instanceof Short
                || number instanceof Integer
                || number instanceof Long) {
            return NumberKind.INTEGRAL;
        }
        if (number instanceof Float || number instanceof Double) {
            return NumberKind.FLOATING;
        }
        return NumberKind.OTHER;
    }

    public static String key(String siteId, String lineId, String tagCode) {
        return siteId + "|" + lineId + "|" + tagCode;
    }

    private enum NumberKind {
        INTEGRAL,
        FLOATING,
        OTHER
    }

    private enum NonFiniteKind {
        FINITE,
        NAN,
        POS_INF,
        NEG_INF
    }

    private static final class TagState {
        final String quality;
        final String statusName;
        final Object lastGoodValue;
        final Object lastEmittedValue;

        private TagState(String quality, String statusName, Object lastGoodValue, Object lastEmittedValue) {
            this.quality = quality;
            this.statusName = statusName;
            this.lastGoodValue = lastGoodValue;
            this.lastEmittedValue = lastEmittedValue;
        }

        static TagState fromFirst(TagReading reading) {
            Object good =
                    QualityGate.requireGood(reading.quality()) && !isNonFinite(reading.value())
                            ? reading.value()
                            : null;
            return new TagState(reading.quality(), reading.statusName(), good, reading.value());
        }

        TagState afterEmit(TagReading reading) {
            Object good = lastGoodValue;
            if (QualityGate.requireGood(reading.quality()) && !isNonFinite(reading.value())) {
                good = reading.value();
            }
            return new TagState(reading.quality(), reading.statusName(), good, reading.value());
        }

        TagState afterUncertainEmit(TagReading reading) {
            return new TagState(reading.quality(), reading.statusName(), lastGoodValue, reading.value());
        }

        TagState afterNonFiniteGood(TagReading reading) {
            // Clears numeric baseline by leaving lastGoodValue null
            return new TagState(reading.quality(), reading.statusName(), null, reading.value());
        }

        TagState withQuality(TagReading reading) {
            return new TagState(reading.quality(), reading.statusName(), lastGoodValue, lastEmittedValue);
        }
    }
}
