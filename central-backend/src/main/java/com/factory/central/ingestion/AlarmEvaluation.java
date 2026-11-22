package com.factory.central.ingestion;

import com.factory.central.cache.AlarmDirection;
import com.factory.central.cache.AlarmSeverity;

public record AlarmEvaluation(AlarmSeverity severity, AlarmDirection direction) {

    public static AlarmEvaluation none() {
        return new AlarmEvaluation(AlarmSeverity.NONE, null);
    }
}
