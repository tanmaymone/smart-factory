package com.factory.edge.mqtt;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;

/** Shared Paho connect options for edge telemetry and command clients. */
public final class MqttClientOptionsFactory {

    private final MqttProperties properties;
    private final SSLSocketFactory socketFactory;

    public MqttClientOptionsFactory(MqttProperties properties) {
        this.properties = properties;
        this.socketFactory = properties.isTls() ? buildSocketFactory(properties.getTls()) : null;
    }

    public MqttConnectOptions connectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[] {properties.normalizedBrokerUrl()});
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        options.setMaxReconnectDelay((int) Math.min(Integer.MAX_VALUE, properties.getReconnectMaxMs()));

        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            options.setUserName(properties.getUsername().trim());
            options.setPassword(properties.getPassword().toCharArray());
        }

        if (properties.isTls()) {
            options.setSocketFactory(socketFactory);
            options.setHttpsHostnameVerificationEnabled(properties.getTls().isVerifyHostname());
        }

        return options;
    }

    public static String describeConnectFailure(MqttException ex) {
        int code = ex.getReasonCode();
        if (code == MqttException.REASON_CODE_FAILED_AUTHENTICATION) {
            return "MQTT auth failed (CONNACK 0x04)";
        }
        if (code == MqttException.REASON_CODE_NOT_AUTHORIZED) {
            return "MQTT not authorized (CONNACK 0x05)";
        }
        Throwable cause = ex.getCause();
        if (cause != null) {
            String name = cause.getClass().getSimpleName();
            String msg = cause.getMessage();
            if (msg != null && !msg.isBlank()) {
                return name + ": " + msg;
            }
            return name;
        }
        String msg = ex.getMessage();
        return msg == null || msg.isBlank() ? "MQTT error code=" + code : msg;
    }

    public static String describeConnectFailure(Throwable cause) {
        if (cause == null) {
            return "unknown";
        }
        if (cause instanceof MqttException mqtt) {
            return describeConnectFailure(mqtt);
        }
        String msg = cause.getMessage();
        return msg == null || msg.isBlank() ? cause.getClass().getSimpleName() : msg;
    }

    private static SSLSocketFactory buildSocketFactory(MqttProperties.Tls tls) {
        String caPath = tls == null ? "" : tls.getCaCertPath();
        if (caPath == null || caPath.isBlank()) {
            try {
                SSLContext ctx = SSLContext.getInstance("TLSv1.3");
                ctx.init(null, null, null);
                return ctx.getSocketFactory();
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to build default TLS socket factory", ex);
            }
        }
        try {
            List<Certificate> certs = loadPemCertificates(Path.of(caPath.trim()));
            if (certs.isEmpty()) {
                throw new IllegalStateException("No X.509 certificates found in: " + caPath);
            }
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(null, null);
            int index = 0;
            for (Certificate cert : certs) {
                store.setCertificateEntry("mqtt-ca-" + index, cert);
                index++;
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(store);
            SSLContext ctx = SSLContext.getInstance("TLSv1.3");
            ctx.init(null, tmf.getTrustManagers(), null);
            return ctx.getSocketFactory();
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load MQTT CA from " + caPath, ex);
        }
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