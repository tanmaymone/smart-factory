package com.factory.central.ingestion;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(TelemetryProperties.class)
public class IngestionConfiguration {

    @Bean
    Clock utcClock() {
        return Clock.systemUTC();
    }

    @Bean
    TelemetryFlushScheduler telemetryFlushScheduler(TelemetryBatchWriter batchWriter) {
        return new TelemetryFlushScheduler(batchWriter);
    }

    static final class TelemetryFlushScheduler {
        private final TelemetryBatchWriter batchWriter;

        TelemetryFlushScheduler(TelemetryBatchWriter batchWriter) {
            this.batchWriter = batchWriter;
        }

        @Scheduled(fixedDelayString = "${app.telemetry.flush-interval-ms:500}")
        void flush() {
            batchWriter.scheduledFlush();
        }
    }
}
