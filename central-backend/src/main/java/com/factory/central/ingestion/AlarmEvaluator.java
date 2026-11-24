package com.factory.central.ingestion;

import com.factory.central.cache.AlarmDirection;
import com.factory.central.cache.AlarmSeverity;
import com.factory.central.command.QualityGate;
import com.factory.central.registry.TagDataType;
import com.factory.central.registry.TagMapping;
import org.springframework.stereotype.Component;

@Component
public class AlarmEvaluator {

    public AlarmEvaluation evaluate(TagMapping tag, Object value, String quality) {
        if (!isGoodQuality(quality)) {
            throw new IllegalArgumentException("Threshold evaluation requires GOOD quality");
        }
        if (tag.getDataType() != TagDataType.FLOAT && tag.getDataType() != TagDataType.INT) {
            return AlarmEvaluation.none();
        }
        if (!(value instanceof Number number)) {
            return AlarmEvaluation.none();
        }
        double numeric = number.doubleValue();

        if (tag.getCriticalLow() != null && numeric <= tag.getCriticalLow()) {
            return new AlarmEvaluation(AlarmSeverity.CRITICAL, AlarmDirection.LOW);
        }
        if (tag.getCriticalHigh() != null && numeric >= tag.getCriticalHigh()) {
            return new AlarmEvaluation(AlarmSeverity.CRITICAL, AlarmDirection.HIGH);
        }
        if (tag.getWarningLow() != null && numeric <= tag.getWarningLow()) {
            return new AlarmEvaluation(AlarmSeverity.WARNING, AlarmDirection.LOW);
        }
        if (tag.getWarningHigh() != null && numeric >= tag.getWarningHigh()) {
            return new AlarmEvaluation(AlarmSeverity.WARNING, AlarmDirection.HIGH);
        }
        return AlarmEvaluation.none();
    }

    public static boolean isGoodQuality(String quality) {
        return QualityGate.requireGood(quality);
    }

    /** Non-good samples (Bad, Uncertain, blank, unknown) freeze threshold evaluation. */
    public static boolean isFrozenQuality(String quality) {
        return !isGoodQuality(quality);
    }
}
