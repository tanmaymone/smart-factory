package com.factory.edge.opcua;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.factory.edge.buffer.StoreAndForwardQueue;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("EDGE-UI")
class OpcUaProbeDiagnosticsTest {

    private OpcUaProperties properties;
    private OpcUaSession session;
    private StoreAndForwardQueue queue;
    private RecordingScheduler scheduler;
    private OpcUaProbe probe;
    private LongConsumer sessionLostCallback;
    private Consumer<TagReading> readingCallback;

    @BeforeEach
    void setUp() {
        properties = new OpcUaProperties();
        properties.setEndpointUrl("opc.tcp://localhost:53530/OPCUA/SimulationServer");
        properties.setReconnectInitialMs(1000);
        properties.setReconnectMaxMs(30000);
        OpcUaProperties.TagConfig tag = new OpcUaProperties.TagConfig();
        tag.setSiteId("INDORE");
        tag.setLineId("LINE-01");
        tag.setTagCode("VALVE_101_POS");
        tag.setNodeId("ns=3;i=1001");
        tag.setDeadband(0.5);
        properties.setTags(List.of(tag));

        session = mock(OpcUaSession.class);
        queue = mock(StoreAndForwardQueue.class);
        scheduler = new RecordingScheduler();
        probe = new OpcUaProbe(properties, session, queue, scheduler);

        doAnswer(
                        invocation -> {
                            readingCallback = invocation.getArgument(1);
                            sessionLostCallback = invocation.getArgument(2);
                            return null;
                        })
                .when(session)
                .start(anyList(), any(), any());
    }

    @Test
    void readingSetsHeartbeatAndObservationIncludingHold() {
        probe.start();
        readingCallback.accept(good(50.0));

        OpcUaLinkHealth.Snapshot health = probe.linkHealth().snapshot();
        assertThat(health.status()).isEqualTo(OpcUaLinkHealth.CONNECTED);
        assertThat(health.lastHeartbeatAt()).isNotNull();
        Instant firstBeat = health.lastHeartbeatAt();

        TagObservationStore.Observation first =
                probe.observations().get("INDORE", "LINE-01", "VALVE_101_POS");
        assertThat(first.value()).isEqualTo(50.0);
        assertThat(first.emitted()).isTrue();
        verify(queue, times(1)).offer(any());

        readingCallback.accept(good(50.2));

        TagObservationStore.Observation held =
                probe.observations().get("INDORE", "LINE-01", "VALVE_101_POS");
        assertThat(held.value()).isEqualTo(50.2);
        assertThat(held.emitted()).isFalse();
        assertThat(held.observedAt()).isAfterOrEqualTo(first.observedAt());
        assertThat(probe.linkHealth().snapshot().lastHeartbeatAt()).isAfterOrEqualTo(firstBeat);
        verify(queue, times(1)).offer(any());
    }

    @Test
    void sessionLossAndFailedReconnectFillLastErrorWithoutClearingHeartbeat() {
        probe.start();
        readingCallback.accept(good(10.0));
        Instant heartbeat = probe.linkHealth().snapshot().lastHeartbeatAt();
        assertThat(heartbeat).isNotNull();

        sessionLostCallback.accept(1L);

        OpcUaLinkHealth.Snapshot lost = probe.linkHealth().snapshot();
        assertThat(lost.status()).isEqualTo(OpcUaLinkHealth.DISCONNECTED);
        assertThat(lost.phase()).isEqualTo("BACKOFF");
        assertThat(lost.lastError()).contains("session lost");
        assertThat(lost.lastHeartbeatAt()).isEqualTo(heartbeat);

        doThrow(new OpcUaProbeException("partial connect failed"))
                .when(session)
                .start(anyList(), any(), any());
        scheduler.runLastTask();

        OpcUaLinkHealth.Snapshot failed = probe.linkHealth().snapshot();
        assertThat(failed.phase()).isEqualTo("BACKOFF");
        assertThat(failed.lastError()).contains("partial connect failed");
        assertThat(failed.lastHeartbeatAt()).isEqualTo(heartbeat);
    }

    private static TagReading good(double value) {
        return new TagReading(
                TagReading.EVENT_READ,
                "INDORE",
                "LINE-01",
                "VALVE_101_POS",
                "ns=3;i=1001",
                value,
                "GOOD",
                Instant.parse("2026-01-15T10:00:00Z"),
                Instant.parse("2026-01-15T10:00:00Z"),
                null);
    }

    private static final class RecordingScheduler extends AbstractExecutorService
            implements ScheduledExecutorService {

        volatile long lastDelayMs;
        volatile int scheduleCount;
        private final AtomicReference<Runnable> lastTask = new AtomicReference<>();

        void runLastTask() {
            Runnable task = lastTask.getAndSet(null);
            if (task != null) {
                task.run();
            }
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            lastDelayMs = unit.toMillis(delay);
            scheduleCount++;
            lastTask.set(command);
            return new ImmediateFuture<>();
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(
                Runnable command, long initialDelay, long period, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(
                Runnable command, long initialDelay, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void shutdown() {}

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        private static final class ImmediateFuture<V> implements ScheduledFuture<V> {
            @Override
            public long getDelay(TimeUnit unit) {
                return 0;
            }

            @Override
            public int compareTo(Delayed o) {
                return 0;
            }

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                return false;
            }

            @Override
            public V get() {
                return null;
            }

            @Override
            public V get(long timeout, TimeUnit unit) {
                return null;
            }
        }
    }
}
