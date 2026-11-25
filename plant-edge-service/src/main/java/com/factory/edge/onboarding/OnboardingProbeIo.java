package com.factory.edge.onboarding;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The one fixed daemon pool that hostname resolution and endpoint discovery share. {@code
 * cancel(true)} does not reap a hung {@code InetAddress} lookup or a half-open discovery channel, so
 * a replacement thread is never started: two abandoned calls fill the pool and the next submit is
 * rejected at once rather than queued.
 */
final class OnboardingProbeIo {

    private static final AtomicLong SERVICE_IDS = new AtomicLong();
    private static final int WORKERS = 2;

    private final String threadPrefix;
    private final ThreadPoolExecutor executor;

    OnboardingProbeIo() {
        this.threadPrefix = "opcua-probe-io-" + SERVICE_IDS.incrementAndGet() + "-";
        AtomicLong workerIds = new AtomicLong();
        ThreadFactory factory =
                runnable -> {
                    Thread thread =
                            new Thread(runnable, threadPrefix + workerIds.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                };
        this.executor =
                new ThreadPoolExecutor(
                        WORKERS,
                        WORKERS,
                        0L,
                        TimeUnit.MILLISECONDS,
                        new SynchronousQueue<>(),
                        factory,
                        new ThreadPoolExecutor.AbortPolicy());
    }

    /** Test seam: a parallel run counts only the workers of its own service. */
    String threadPrefix() {
        return threadPrefix;
    }

    <T> T await(Callable<T> task, OnboardingProbeDeadline deadline) {
        if (deadline.expired()) {
            throw OnboardingProbeFailure.timedOut();
        }
        Future<T> future;
        try {
            future = executor.submit(task);
        } catch (RejectedExecutionException e) {
            // Do not wait out the remaining budget once submit has been rejected.
            throw OnboardingProbeFailure.poolSaturated();
        }
        try {
            return future.get(deadline.remainingMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw OnboardingProbeFailure.timedOut();
        } catch (ExecutionException e) {
            future.cancel(true);
            throw OnboardingProbeFailure.of(causeMessage(e));
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw OnboardingProbeFailure.interrupted();
        }
    }

    void shutdownNow() {
        executor.shutdownNow();
    }

    private static String causeMessage(ExecutionException e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }
}
