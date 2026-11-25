package com.factory.central.registry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("SF-REGISTRY")
class EdgeConfigIfNoneMatchTest {

    @Test
    void matchesStrongValidatorsAndIgnoresWeak() {
        assertThat(EdgeConfigController.matchesIfNoneMatch("\"v12\"", "v12")).isTrue();
        assertThat(EdgeConfigController.matchesIfNoneMatch("v12", "v12")).isTrue();
        assertThat(EdgeConfigController.matchesIfNoneMatch("\"v12\", \"v12\"", "v12")).isTrue();
        assertThat(EdgeConfigController.matchesIfNoneMatch("W/\"vOther\", \"v12\"", "v12")).isTrue();
        assertThat(EdgeConfigController.matchesIfNoneMatch("W/\"v12\", \"v12\"", "v12")).isTrue();
        assertThat(EdgeConfigController.matchesIfNoneMatch("W/\"v12\", \"vOther\"", "v12")).isFalse();
        assertThat(EdgeConfigController.matchesIfNoneMatch("W/\"v12\"", "v12")).isFalse();
    }

    @Test
    void emptyOrBlankHeaderMeansNoPrecondition() {
        assertThat(EdgeConfigController.matchesIfNoneMatch(null, "v1")).isFalse();
        assertThat(EdgeConfigController.matchesIfNoneMatch("", "v1")).isFalse();
        assertThat(EdgeConfigController.matchesIfNoneMatch("   ", "v1")).isFalse();
        assertThat(EdgeConfigController.matchesIfNoneMatch("\"\"", "v1")).isFalse();
    }
}
