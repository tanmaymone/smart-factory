package com.factory.edge.opcua;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.factory.edge.buffer.StoreAndForwardQueue;
import com.factory.edge.buffer.TelemetryRecord;
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
import org.mockito.ArgumentCaptor;

@Tag("EDGE-RECONNECT")
class OpcUaProbeReconnectTest {

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
        tag.setNodeId("nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1001");
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
    void sessionLossSchedulesBackoffWithoutExit() {
        probe.start();
        assertThat(probe.currentPhase()).isEqualTo(OpcUaProbe.Phase.UP);

        sessionLostCallback.accept(1L);

        assertThat(probe.currentPhase()).isEqualTo(OpcUaProbe.Phase.BACKOFF);
        assertThat(scheduler.lastDelayMs).isEqualTo(1000L);
        verify(session, atLeastOnce()).close();
        assertThat(probe.isRunning()).isTrue();
    }

    @Test
    void unavailableAtStartEntersBackoffThenRecovers() {
        doThrow(new OpcUaUnavailableException("OPC UA discovery failed: Connection refused"))
                .doAnswer(
                        invocation -> {
                            readingCallback = invocation.getArgument(1);
                            sessionLostCallback = invocation.getArgument(2);
                            return null;
                        })
                .when(session)
                .start(anyList(), any(), any());

        probe.start();

        assertThat(probe.currentPhase()).isEqualTo(OpcUaProbe.Phase.BACKOFF);
        assertThat(probe.isRunning()).isTrue();
        assertThat(scheduler.scheduleCount).isEqualTo(1);
        assertThat(scheduler.lastDelayMs).isEqualTo(1000L);
        assertThat(probe.linkHealth().snapshot().lastError())
                .contains("OPC UA server is not live at")
                .contains(properties.getEndpointUrl());

        scheduler.runLastTask();

        assertThat(probe.currentPhase()).isEqualTo(OpcUaProbe.Phase.UP);
        assertThat(probe.linkHealth().snapshot().status()).isEqualTo(OpcUaLinkHealth.CONNECTED);
        assertThat(probe.currentBackoffMs()).isEqualTo(1000L);
        verify(session, times(2)).start(anyList(), any(), any());
    }

    @Test
    void duplicateLossWhileBackoffDoesNotScheduleSecondChain() {
        probe.start();
        sessionLostCallback.accept(1L);
        long firstDelay = scheduler.lastDelayMs;
        int schedules = scheduler.scheduleCount;

        sessionLostCallback.accept(1L);
        sessionLostCallback.accept(2L);

        assertThat(scheduler.scheduleCount).isEqualTo(schedules);
        assertThat(scheduler.lastDelayMs).isEqualTo(firstDelay);
        assertThat(probe.currentPhase()).isEqualTo(OpcUaProbe.Phase.BACKOFF);
    }

    @Test
    void failedPartialStartAdvancesBackoffOnce() {
        probe.start();
        sessionLostCallback.accept(1L);
        assertThat(scheduler.lastDelayMs).isEqualTo(1000L);

        doThrow(new OpcUaProbeException("partial connect failed"))
                .when(session)
                .start(anyList(), any(), any());

        scheduler.runLastTask();

        assertThat(probe.currentPhase()).isEqualTo(OpcUaProbe.Phase.BACKOFF);
        assertThat(probe.currentBackoffMs()).isEqualTo(2000L);
        assertThat(scheduler.lastDelayMs).isEqualTo(2000L);
        verify(session, atLeastOnce()).close();
    }

    @Test
    void successfulReconnectPreservesDeadbandBaseline() {
        probe.start();
        readingCallback.accept(good(50.0));
        ArgumentCaptor<TelemetryRecord> captor = ArgumentCaptor.forClass(TelemetryRecord.class);
        verify(queue).offer(captor.capture());
        assertThat(captor.getValue().value()).isEqualTo(50.0);

        sessionLostCallback.accept(1L);

        doAnswer(
                        invocation -> {
                            readingCallback = invocation.getArgument(1);
                            sessionLostCallback = invocation.getArgument(2);
                            // reconnect READ of same value
                            readingCallback.accept(good(50.2));
                            return null;
                        })
                .when(session)
                .start(anyList(), any(), any());

        scheduler.runLastTask();

        assertThat(probe.currentPhase()).isEqualTo(OpcUaProbe.Phase.UP);
        assertThat(probe.currentBackoffMs()).isEqualTo(1000L);
        // 50.2 is inside deadband of 50.0 — no second offer
        verify(queue, times(1)).offer(any());
    }

    private static TagReading good(double value) {
        return new TagReading(
                TagReading.EVENT_READ,
                "INDORE",
                "LINE-01",
                "VALVE_101_POS",
                "nsu=http://www.prosysopc.com/OPCUA/SimulationNodes/;i=1001",
                value,
                "GOOD",
                Instant.parse("2026-11-20T12:00:00Z"),
                Instant.parse("2026-11-20T12:00:00Z"),
                null,
                null);
    }

    /** Records schedule delays and runs tasks synchronously when asked. */
    private static final class RecordingScheduler extends AbstractExecutorService
            implements ScheduledExecutorService {

        volatile long lastDelayMs = -1;
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
                return true;
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
