package com.factory.central.registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("SF-REGISTRY")
class RegistryLivenessTest {

    @Test
    void requiresSiteLineAndTagActive() {
        Site site = new Site("NASHIK", "Nashik", "Asia/Kolkata", SiteStatus.ONLINE, RegistryLifecycle.ACTIVE);
        Line line = new Line("NASHIK", "LINE-01", "Reactor", RegistryLifecycle.ACTIVE);
        TagMapping tag = tag(RegistryLifecycle.ACTIVE);

        assertThat(RegistryLiveness.isLive(site, line, tag)).isTrue();
        assertThat(RegistryLiveness.isLive(
                        new Site("NASHIK", "Nashik", "Asia/Kolkata", SiteStatus.ONLINE, RegistryLifecycle.DRAFT),
                        line,
                        tag))
                .isFalse();
        assertThat(RegistryLiveness.isLive(
                        site, new Line("NASHIK", "LINE-01", "Reactor", RegistryLifecycle.DRAFT), tag))
                .isFalse();
        assertThat(RegistryLiveness.isLive(site, line, tag(RegistryLifecycle.DRAFT))).isFalse();
        assertThat(RegistryLiveness.isLive(null, line, tag)).isFalse();
    }

    private static TagMapping tag(RegistryLifecycle lifecycle) {
        return new TagMapping(
                UUID.randomUUID(),
                "NASHIK",
                "LINE-01",
                "REACTOR_TEMP_C",
                "temp",
                Protocol.OPC_UA,
                "opc.tcp://localhost",
                "ns=3;i=1002",
                TagDataType.FLOAT,
                "\u00B0C",
                false,
                null,
                null,
                0.2,
                null,
                null,
                null,
                null,
                null,
                lifecycle);
    }
}
