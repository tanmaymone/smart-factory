package com.factory.central.onboarding;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Loopback stand-in for plant-edge-service. The JDK HTTP server keeps the forward tests off any
 * extra test dependency, and the recorded request list is what asserts "the edge was not called".
 */
final class StubEdgeServer implements AutoCloseable {

    private final HttpServer server;
    private final List<byte[]> requests = new CopyOnWriteArrayList<>();

    private volatile int status = 200;
    private volatile String contentType = "application/json";
    private volatile byte[] body = "{\"reachable\":true,\"tags\":[]}".getBytes(UTF_8);

    private StubEdgeServer(HttpServer server) {
        this.server = server;
    }

    static StubEdgeServer start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        StubEdgeServer stub = new StubEdgeServer(server);
        server.createContext("/api/onboarding/probe", exchange -> {
            stub.requests.add(exchange.getRequestBody().readAllBytes());
            if (stub.contentType != null) {
                exchange.getResponseHeaders().set("Content-Type", stub.contentType);
            }
            byte[] payload = stub.body;
            exchange.sendResponseHeaders(stub.status, payload.length == 0 ? -1 : payload.length);
            if (payload.length > 0) {
                exchange.getResponseBody().write(payload);
            }
            exchange.close();
        });
        server.start();
        return stub;
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void respondWith(int status, String contentType, String body) {
        this.status = status;
        this.contentType = contentType;
        this.body = body.getBytes(UTF_8);
    }

    List<byte[]> requests() {
        return Collections.unmodifiableList(requests);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
