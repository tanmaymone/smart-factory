package com.factory.edge.onboarding;

import com.factory.edge.opcua.OnboardingOpcUaBridge;
import com.factory.edge.opcua.OpcUaProperties;
import com.factory.edge.opcua.TagReading;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * One request-scoped probe at a time. The 8s budget starts only after {@code tryLock()} succeeds and
 * covers DNS, so the worst lock hold is about 10s with the bounded disconnect — under Central's 15s
 * forward. The live {@code MiloOpcUaSession} is never started or closed from here, and teardown
 * stops at this client's {@code disconnect()}: releasing Milo's shared stack would drop the live
 * subscription session too.
 */
@Service
public class OnboardingProbeService {

    /** Test seam for a lookup that never completes; production resolves on the probe I/O pool. */
    @FunctionalInterface
    interface HostResolver {
        InetAddress resolve(String host) throws Exception;
    }

    private static final Logger log = LoggerFactory.getLogger(OnboardingProbeService.class);
    private static final long DEFAULT_BUDGET_MS = 8_000L;
    private static final long DISCONNECT_TIMEOUT_MS = 2_000L;

    private final ReentrantLock lock = new ReentrantLock();
    private final OnboardingProbeIo io = new OnboardingProbeIo();
    private final OnboardingOpcUaBridge bridge;
    private final OpcUaProperties opcUaProperties;
    private final long budgetMs;
    private final HostResolver hostResolver;

    public OnboardingProbeService(OnboardingOpcUaBridge bridge, OpcUaProperties opcUaProperties) {
        this(bridge, opcUaProperties, DEFAULT_BUDGET_MS, InetAddress::getByName);
    }

    OnboardingProbeService(
            OnboardingOpcUaBridge bridge,
            OpcUaProperties opcUaProperties,
            long budgetMs,
            HostResolver hostResolver) {
        this.bridge = bridge;
        this.opcUaProperties = opcUaProperties;
        this.budgetMs = budgetMs;
        this.hostResolver = hostResolver;
    }

    @PreDestroy
    void shutdown() {
        // Does not wait for a worker that ignores interruption; the threads are daemons.
        io.shutdownNow();
    }

    String ioThreadPrefix() {
        return io.threadPrefix();
    }

    /** Empty means the lock is already held: 409 {@code PROBE_IN_PROGRESS}, with no wait. */
    public Optional<OnboardingProbeResponse> probe(
            OnboardingProbeRequest request, OnboardingProbeUrl url) {
        if (!lock.tryLock()) {
            return Optional.empty();
        }
        OpcUaClient client = null;
        try {
            OnboardingProbeDeadline deadline = OnboardingProbeDeadline.start(budgetMs);
            String pkiDir = opcUaProperties.getSecurity().getPkiDir();
            if (OnboardingProbeValidator.SECURITY_MODE_SIGN_AND_ENCRYPT.equals(
                    request.securityMode())) {
                // First action after tryLock: no LAN socket and no pool worker when PKI fails.
                String pkiFailure = bridge.pkiFailure(pkiDir);
                if (pkiFailure != null) {
                    return Optional.of(OnboardingProbeTags.unreachable(request, pkiFailure));
                }
            }
            InetAddress address = resolve(url, deadline);
            // getEndpoints already dials. A closed or filtered port fails here, on this thread,
            // so it does not pin an opcua-probe-io worker.
            connectTcp(address, url.port(), deadline);
            EndpointDescription endpoint = discover(url, request.securityMode(), deadline);
            client =
                    bridge.createForProbe(
                            endpoint,
                            url.dialUrl(),
                            request.securityMode(),
                            pkiDir,
                            deadline.remainingMillis());
            awaitConnect(client, deadline);
            return Optional.of(OnboardingProbeReader.read(client, request, deadline));
        } catch (OnboardingProbeFailure e) {
            if (e.isInterrupted()) {
                Thread.currentThread().interrupt();
            }
            return Optional.of(OnboardingProbeTags.unreachable(request, e.getMessage()));
        } catch (RejectedExecutionException e) {
            return Optional.of(
                    OnboardingProbeTags.unreachable(request, OnboardingProbeFailure.POOL_SATURATED));
        } catch (RuntimeException e) {
            // PKI and transport IllegalStateException on this path are a 200, never a 500.
            return Optional.of(OnboardingProbeTags.unreachable(request, message(e)));
        } finally {
            try {
                disconnect(client);
            } finally {
                lock.unlock();
            }
        }
    }

    private InetAddress resolve(OnboardingProbeUrl url, OnboardingProbeDeadline deadline) {
        // createUnresolved: a resolving InetSocketAddress would block the probe thread untimed.
        InetSocketAddress target = InetSocketAddress.createUnresolved(url.host(), url.port());
        return io.await(
                () -> {
                    try {
                        return hostResolver.resolve(target.getHostString());
                    } catch (UnknownHostException e) {
                        throw new IOException(
                                "DNS resolution failed for host " + target.getHostString(), e);
                    }
                },
                deadline);
    }

    private EndpointDescription discover(
            OnboardingProbeUrl url, String securityMode, OnboardingProbeDeadline deadline) {
        // getEndpoints().get(), the selection, and the rewrite all run on the worker.
        return io.await(
                () ->
                        bridge.discoverAndRewriteForProbe(
                                url.dialUrl(), securityMode, deadline.remainingMillis()),
                deadline);
    }

    private static void connectTcp(
            InetAddress address, int port, OnboardingProbeDeadline deadline) {
        if (deadline.expired()) {
            throw OnboardingProbeFailure.timedOut();
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(address, port), (int) deadline.remainingMillis());
        } catch (SocketTimeoutException e) {
            throw OnboardingProbeFailure.timedOut();
        } catch (IOException e) {
            throw OnboardingProbeFailure.of(
                    "TCP connect failed for " + address.getHostAddress() + ":" + port + ": "
                            + e.getMessage());
        }
    }

    private static void awaitConnect(OpcUaClient client, OnboardingProbeDeadline deadline) {
        if (deadline.expired()) {
            throw OnboardingProbeFailure.timedOut();
        }
        try {
            client.connectAsync().get(deadline.remainingMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw OnboardingProbeFailure.timedOut();
        } catch (ExecutionException e) {
            throw OnboardingProbeFailure.of(connectMessage(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw OnboardingProbeFailure.interrupted();
        }
    }

    /** Runs whenever the client reference was assigned, including when connect() timed out. */
    private static void disconnect(OpcUaClient client) {
        if (client == null) {
            return;
        }
        try {
            client.disconnectAsync().get(DISCONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // Keeps the try-path result; the servlet thread must not swallow the interrupt.
            Thread.currentThread().interrupt();
        } catch (TimeoutException | ExecutionException e) {
            log.debug("Probe disconnect abandoned: {}", e.toString());
        }
    }

    private static String connectMessage(ExecutionException e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        if (cause instanceof UaException ua && ua.getStatusCode() != null) {
            return TagReading.symbolicName(ua.getStatusCode())
                    + ": "
                    + (ua.getMessage() == null ? ua.toString() : ua.getMessage());
        }
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }

    private static String message(RuntimeException e) {
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }
}
