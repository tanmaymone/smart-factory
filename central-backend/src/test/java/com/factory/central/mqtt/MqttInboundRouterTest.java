package com.factory.central.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("SF-M1.6")
class MqttInboundRouterTest {

    @Test
    void topicMatchesWildcards() {
        assertThat(MqttInboundRouter.topicMatches("site/+/line/+/telemetry", "site/INDORE/line/LINE-01/telemetry"))
                .isTrue();
        assertThat(MqttInboundRouter.topicMatches(
                        "site/+/line/+/commands/acks", "site/INDORE/line/LINE-01/commands/acks"))
                .isTrue();
        assertThat(MqttInboundRouter.topicMatches("site/+/line/+/telemetry", "site/INDORE/line/LINE-01/commands"))
                .isFalse();
    }
}
