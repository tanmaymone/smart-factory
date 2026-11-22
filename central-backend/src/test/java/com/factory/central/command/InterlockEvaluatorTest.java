package com.factory.central.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.factory.central.cache.InMemoryLatestTagCache;
import com.factory.central.registry.Protocol;
import com.factory.central.registry.TagDataType;
import com.factory.central.registry.TagMapping;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("SF-M1.6")
class InterlockEvaluatorTest {

    @Test
    void blankKeyPasses() {
        InterlockEvaluator evaluator = new InterlockEvaluator(List.of(new InletValveOpenInterlockRule()));
        TagMapping tag = tag(null);
        assertThat(evaluator.evaluate(null, new InterlockContext(tag, 1.0, new InMemoryLatestTagCache())))
                .isEmpty();
        assertThat(evaluator.evaluate("  ", new InterlockContext(tag, 1.0, new InMemoryLatestTagCache())))
                .isEmpty();
    }

    @Test
    void unknownKeyRejects() {
        InterlockEvaluator evaluator = new InterlockEvaluator(List.of());
        Optional<String> reason =
                evaluator.evaluate("MISSING", new InterlockContext(tag(null), 1.0, new InMemoryLatestTagCache()));
        assertThat(reason).contains("Unknown interlock rule key: MISSING");
    }

    @Test
    void duplicateKeysFailStartup() {
        assertThatThrownBy(() -> new InterlockEvaluator(
                        List.of(new InletValveOpenInterlockRule(), new InletValveOpenInterlockRule())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate InterlockRule key");
    }

    private static TagMapping tag(String interlockKey) {
        return new TagMapping(
                UUID.randomUUID(),
                "INDORE",
                "LINE-01",
                "REACTOR_TEMP_SP",
                "sp",
                Protocol.OPC_UA,
                "opc.tcp://localhost",
                "ns=3;i=1",
                TagDataType.FLOAT,
                "°C",
                true,
                15.0,
                90.0,
                0.1,
                null,
                null,
                null,
                null,
                interlockKey);
    }
}
