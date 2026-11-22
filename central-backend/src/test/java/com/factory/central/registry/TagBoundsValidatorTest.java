package com.factory.central.registry;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("SF-M1.3")
class TagBoundsValidatorTest {

    @Test
    void acceptsAllNullThresholds() {
        assertThatCode(() -> TagBoundsValidator.validate(request(null, null, null, null, null, null)))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsFullOrderedBand() {
        assertThatCode(() -> TagBoundsValidator.validate(request(null, null, 30.0, 70.0, 20.0, 80.0)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsInvertedWarningPair() {
        assertThatThrownBy(() -> TagBoundsValidator.validate(request(null, null, 70.0, 30.0, null, null)))
                .isInstanceOf(RegistryException.class)
                .extracting(ex -> ((RegistryException) ex).getCode())
                .isEqualTo(RegistryErrorCode.INVALID_BOUNDS);
    }

    @Test
    void rejectsInvertedCriticalPair() {
        assertThatThrownBy(() -> TagBoundsValidator.validate(request(null, null, null, null, 80.0, 20.0)))
                .isInstanceOf(RegistryException.class)
                .extracting(ex -> ((RegistryException) ex).getCode())
                .isEqualTo(RegistryErrorCode.INVALID_BOUNDS);
    }

    @Test
    void rejectsCriticalLowAboveWarningLow() {
        assertThatThrownBy(() -> TagBoundsValidator.validate(request(null, null, 40.0, null, 50.0, null)))
                .isInstanceOf(RegistryException.class)
                .extracting(ex -> ((RegistryException) ex).getCode())
                .isEqualTo(RegistryErrorCode.INVALID_BOUNDS);
    }

    @Test
    void rejectsWarningHighAboveCriticalHigh() {
        assertThatThrownBy(() -> TagBoundsValidator.validate(request(null, null, null, 90.0, null, 80.0)))
                .isInstanceOf(RegistryException.class)
                .extracting(ex -> ((RegistryException) ex).getCode())
                .isEqualTo(RegistryErrorCode.INVALID_BOUNDS);
    }

    @Test
    void rejectsMinAboveMax() {
        assertThatThrownBy(() -> TagBoundsValidator.validate(request(100.0, 0.0, null, null, null, null)))
                .isInstanceOf(RegistryException.class)
                .extracting(ex -> ((RegistryException) ex).getCode())
                .isEqualTo(RegistryErrorCode.INVALID_BOUNDS);
    }

    private static TagMappingRequest request(
            Double minValue,
            Double maxValue,
            Double warningLow,
            Double warningHigh,
            Double criticalLow,
            Double criticalHigh) {
        return new TagMappingRequest(
                "INDORE",
                "LINE-01",
                "TAG",
                "desc",
                Protocol.OPC_UA,
                "opc.tcp://localhost:53530/OPCUA/SimulationServer",
                "ns=3;i=1",
                TagDataType.FLOAT,
                "°C",
                false,
                minValue,
                maxValue,
                0.1,
                warningLow,
                warningHigh,
                criticalLow,
                criticalHigh,
                null);
    }
}
