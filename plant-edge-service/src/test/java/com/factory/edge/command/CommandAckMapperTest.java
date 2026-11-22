package com.factory.edge.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("EDGE-CMD")
class CommandAckMapperTest {

    @Test
    void rejectionEmitsExplicitNullValueFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        CommandAckMapper ackMapper = new CommandAckMapper(mapper);
        CommandResult rejected =
                CommandResult.rejected(
                        "c1",
                        "INDORE",
                        "LINE-01",
                        "VALVE_101_POS",
                        "ns=3;i=1001",
                        null,
                        "unknown tag",
                        Instant.parse("2026-11-21T12:00:00Z"),
                        true);

        JsonNode json = mapper.readTree(ackMapper.toJson(rejected));
        assertThat(json.get("appliedValue").isNull()).isTrue();
        assertThat(json.get("observedValue").isNull()).isTrue();
        assertThat(json.path("errorMessage").asText()).isEqualTo("unknown tag");
        assertThat(json.path("reason").asText()).isEqualTo("unknown tag");
        assertThat(json.has("appliedValue")).isTrue();
        assertThat(json.has("observedValue")).isTrue();
        assertThat(json.has("errorMessage")).isTrue();
        assertThat(json.has("reason")).isTrue();
    }
}
