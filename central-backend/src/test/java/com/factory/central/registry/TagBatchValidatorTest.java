package com.factory.central.registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("SF-REGISTRY")
class TagBatchValidatorTest {

    @Test
    void rejectsEqualMinAndMax() {
        List<TagBatchRowError> errors = validate(row(Map.of(
                "minValue", "10",
                "maxValue", "10")));
        assertThat(errors)
                .anyMatch(err -> "minValue".equals(err.field())
                        && RegistryErrorCode.INVALID_BOUNDS.equals(err.code()));
    }

    @Test
    void rejectsNegativeDeadband() {
        List<TagBatchRowError> errors = validate(row(Map.of("deadband", "-0.1")));
        assertThat(errors).anyMatch(err -> "deadband".equals(err.field()));
    }

    @Test
    void rejectsUnknownProtocol() {
        List<TagBatchRowError> errors = validate(row(Map.of("protocol", "PROFIBUS")));
        assertThat(errors).anyMatch(err -> "protocol".equals(err.field()));
    }

    @Test
    void rejectsBlankExternalAddress() {
        List<TagBatchRowError> errors = validate(row(Map.of("externalAddress", "  ")));
        assertThat(errors).anyMatch(err -> "externalAddress".equals(err.field()));
    }

    private static List<TagBatchRowError> validate(TagBatchRowInput input) {
        return TagBatchValidator.validate(input, new TagBatchValidator.ParsedRowHolder());
    }

    private static TagBatchRowInput row(Map<String, String> overrides) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("siteId", "NASHIK");
        fields.put("lineId", "LINE-01");
        fields.put("tagCode", "REACTOR_TEMP_C");
        fields.put("description", "Reactor temperature");
        fields.put("protocol", "OPC_UA");
        fields.put("endpointUrl", "opc.tcp://localhost:53530/OPCUA/SimulationServer");
        fields.put("externalAddress", "ns=3;i=1002");
        fields.put("dataType", "FLOAT");
        fields.put("writable", "false");
        fields.put("deadband", "0.2");
        fields.put("minValue", "0");
        fields.put("maxValue", "100");
        fields.putAll(overrides);
        return new TagBatchRowInput(fields, null, false);
    }
}
