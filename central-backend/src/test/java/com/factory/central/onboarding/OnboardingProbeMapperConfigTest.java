package com.factory.central.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The probe mapper must not be published as an ObjectMapper bean: that makes Boot's auto-configured
 * primary mapper back off and hands the CoercionConfig copy to the whole application.
 */
@Tag("EDGE-PROBE")
class OnboardingProbeMapperConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(
                    org.springframework.boot.autoconfigure.AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withUserConfiguration(OnboardingProbeMapperConfig.class);

    @Test
    void probeMapperLeavesTheApplicationMapperInPlaceAndPropertiesDefaultOff() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeanNamesForType(ObjectMapper.class)).containsExactly("jacksonObjectMapper");
            assertThat(context.getBean(OnboardingProbeMapper.class).mapper())
                    .isNotSameAs(context.getBean(ObjectMapper.class));

            OnboardingProbeProperties properties = context.getBean(OnboardingProbeProperties.class);
            assertThat(properties.isProbeEnabled()).isFalse();
            assertThat(properties.getEdgeBaseUrl()).isEqualTo("http://127.0.0.1:8081");
        });
    }

    @Test
    void probeMapperFailsScalarsIntoTextualSlotsWhileTheApplicationMapperCoerces() throws Exception {
        ObjectMapper applicationMapper = ProbeTestSupport.applicationMapper();
        assertThat(applicationMapper
                        .readValue("{\"securityMode\":1}", OnboardingProbeRequest.class)
                        .securityMode())
                .isEqualTo("1");

        OnboardingProbeMapper probeMapper = ProbeTestSupport.probeMapper();
        for (String body : new String[] {
            "{\"securityMode\":1}",
            "{\"securityMode\":1.5}",
            "{\"securityMode\":true}",
            "{\"endpointUrl\":123}",
            "{\"nodeIds\":[\"a\",5]}"
        }) {
            assertThatExceptionOfType(JacksonException.class)
                    .isThrownBy(() -> probeMapper.read(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
    }
}
