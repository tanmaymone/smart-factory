package com.factory.central.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.factory.central.cache.AlarmDirection;
import com.factory.central.cache.AlarmSeverity;
import com.factory.central.registry.Protocol;
import com.factory.central.registry.TagDataType;
import com.factory.central.registry.TagMapping;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("SF-M1.4")
class AlarmEvaluatorTest {

    private final AlarmEvaluator evaluator = new AlarmEvaluator();

    @Test
    void evaluatesSeedShapedTemperatureBandsInclusively() {
        TagMapping tag = tempTag(30.0, 70.0, 20.0, 80.0);

        assertThat(evaluator.evaluate(tag, 20.0, "GOOD").severity()).isEqualTo(AlarmSeverity.CRITICAL);
        assertThat(evaluator.evaluate(tag, 20.0, "GOOD").direction()).isEqualTo(AlarmDirection.LOW);
        assertThat(evaluator.evaluate(tag, 80.0, "GOOD").severity()).isEqualTo(AlarmSeverity.CRITICAL);
        assertThat(evaluator.evaluate(tag, 80.0, "GOOD").direction()).isEqualTo(AlarmDirection.HIGH);
        assertThat(evaluator.evaluate(tag, 30.0, "GOOD").severity()).isEqualTo(AlarmSeverity.WARNING);
        assertThat(evaluator.evaluate(tag, 70.0, "GOOD").severity()).isEqualTo(AlarmSeverity.WARNING);
        assertThat(evaluator.evaluate(tag, 50.0, "GOOD").severity()).isEqualTo(AlarmSeverity.NONE);
    }

    @Test
    void nullThresholdsProduceNone() {
        TagMapping tag = tempTag(null, null, null, null);
        assertThat(evaluator.evaluate(tag, 999.0, "GOOD").severity()).isEqualTo(AlarmSeverity.NONE);
    }

    @Test
    void booleanTagProducesNone() {
        TagMapping tag = new TagMapping(
                UUID.randomUUID(),
                "INDORE",
                "LINE-01",
                "PUMP_RUN",
                "Pump",
                Protocol.OPC_UA,
                "opc.tcp://localhost",
                "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=9",
                TagDataType.BOOLEAN,
                null,
                false,
                null,
                null,
                0.0,
                null,
                null,
                null,
                null,
                null);
        assertThat(evaluator.evaluate(tag, true, "GOOD").severity()).isEqualTo(AlarmSeverity.NONE);
    }

    @Test
    void criticalTakesPrecedenceOverWarning() {
        TagMapping tag = tempTag(30.0, 70.0, 20.0, 80.0);
        assertThat(evaluator.evaluate(tag, 10.0, "GOOD").severity()).isEqualTo(AlarmSeverity.CRITICAL);
    }

    private static TagMapping tempTag(
            Double warningLow, Double warningHigh, Double criticalLow, Double criticalHigh) {
        return new TagMapping(
                UUID.randomUUID(),
                "INDORE",
                "LINE-01",
                "REACTOR_TEMP_C",
                "Reactor temperature",
                Protocol.OPC_UA,
                "opc.tcp://localhost",
                "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1002",
                TagDataType.FLOAT,
                "Â\u00B0C",
                false,
                null,
                null,
                0.2,
                warningLow,
                warningHigh,
                criticalLow,
                criticalHigh,
                null);
    }
}
