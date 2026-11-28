package com.factory.edge.config;

import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

/** Builds JDK HttpClient with optional PEM CA trust for Central HTTPS config sync. */
final class HttpTlsSupport {

    private HttpTlsSupport() {}

    static HttpClient buildHttpClient(String caCertPath, long timeoutMs) {
        Duration timeout = Duration.ofMillis(Math.max(100L, timeoutMs));
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(timeout);
        if (caCertPath == null || caCertPath.isBlank()) {
            return builder.build();
        }
        try {
            SSLContext ctx = sslContextFromPem(Path.of(caCertPath.trim()));
            SSLParameters params = new SSLParameters();
            params.setEndpointIdentificationAlgorithm("HTTPS");
            return builder.sslContext(ctx).sslParameters(params).build();
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load Central CA from " + caCertPath, ex);
        }
    }

    private static SSLContext sslContextFromPem(Path path) throws Exception {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalStateException("Central CA cert path is not readable: " + path);
        }
        List<Certificate> certs = loadPemCertificates(path);
        if (certs.isEmpty()) {
            throw new IllegalStateException("No X.509 certificates found in: " + path);
        }
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null, null);
        int index = 0;
        for (Certificate cert : certs) {
            store.setCertificateEntry("central-ca-" + index, cert);
            index++;
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(store);
        SSLContext ctx = SSLContext.getInstance("TLSv1.3");
        ctx.init(null, tmf.getTrustManagers(), null);
        return ctx;
    }

    private static List<Certificate> loadPemCertificates(Path path) throws Exception {
        String pem = Files.readString(path, StandardCharsets.UTF_8);
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        List<Certificate> out = new ArrayList<>();
        int start = 0;
        while (true) {
            int begin = pem.indexOf("-----BEGIN CERTIFICATE-----", start);
            if (begin < 0) {
                break;
            }
            int end = pem.indexOf("-----END CERTIFICATE-----", begin);
            if (end < 0) {
                break;
            }
            end += "-----END CERTIFICATE-----".length();
            String block = pem.substring(begin, end);
            try (ByteArrayInputStream in = new ByteArrayInputStream(block.getBytes(StandardCharsets.US_ASCII))) {
                Collection<? extends Certificate> parsed = factory.generateCertificates(in);
                out.addAll(parsed);
            }
            start = end;
        }
        return out;
    }
}