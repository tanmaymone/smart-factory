package com.factory.edge.opcua;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.stream.Stream;
import org.eclipse.milo.opcua.stack.core.security.CertificateQuarantine;
import org.eclipse.milo.opcua.stack.core.security.CertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.DefaultClientCertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.FileBasedTrustListManager;
import org.eclipse.milo.opcua.stack.core.security.MemoryCertificateQuarantine;

/**
 * Loads client key material and trust list from {@code app.opcua.security.pki-dir}. Layout:
 *
 * <pre>
 * pki-dir/
 *   own/cert.der
 *   own/private-key.pem
 *   trusted/certs/   (non-empty)
 *   trusted/crl/
 *   issuer/certs/
 *   issuer/crl/
 * </pre>
 */
final class OpcUaPkiMaterial {

    record Loaded(
            KeyPair keyPair,
            X509Certificate certificate,
            CertificateValidator certificateValidator,
            AutoCloseable trustListCloseable) {}

    private OpcUaPkiMaterial() {}

    static Loaded load(String pkiDir) {
        Path root = requireReadableDir(pkiDir, "app.opcua.security.pki-dir");
        Path ownCert = root.resolve("own").resolve("cert.der");
        Path ownKey = root.resolve("own").resolve("private-key.pem");
        Path trustedCerts = root.resolve("trusted").resolve("certs");
        requireReadableFile(ownCert, "own/cert.der");
        requireReadableFile(ownKey, "own/private-key.pem");
        requireReadableDir(trustedCerts.toString(), "trusted/certs");
        if (!hasCertificateFiles(trustedCerts)) {
            throw new IllegalStateException(
                    "app.opcua.security.pki-dir trusted/certs is empty; place the OPC UA server"
                            + " certificate there before using Basic256Sha256");
        }

        try {
            X509Certificate certificate = readCertificate(ownCert);
            PrivateKey privateKey = readPrivateKey(ownKey);
            KeyPair keyPair = new KeyPair(certificate.getPublicKey(), privateKey);

            // Ensure issuer/crl dirs exist so FileBasedTrustListManager can open them.
            Files.createDirectories(root.resolve("issuer").resolve("certs"));
            Files.createDirectories(root.resolve("issuer").resolve("crl"));
            Files.createDirectories(root.resolve("trusted").resolve("crl"));

            FileBasedTrustListManager trustList = FileBasedTrustListManager.createAndInitialize(root);
            CertificateQuarantine quarantine = new MemoryCertificateQuarantine();
            CertificateValidator validator =
                    new DefaultClientCertificateValidator(trustList, quarantine);
            return new Loaded(keyPair, certificate, validator, trustList);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to load OPC UA PKI from " + root + ": " + e.getMessage(), e);
        }
    }

    private static Path requireReadableDir(String pathText, String label) {
        if (pathText == null || pathText.isBlank()) {
            throw new IllegalStateException(label + " is required for Basic256Sha256");
        }
        Path path = Path.of(pathText).toAbsolutePath().normalize();
        if (!Files.isDirectory(path) || !Files.isReadable(path)) {
            throw new IllegalStateException(label + " must be a readable directory: " + path);
        }
        return path;
    }

    private static void requireReadableFile(Path path, String label) {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalStateException(
                    "app.opcua.security.pki-dir " + label + " must be a readable file: " + path);
        }
    }

    private static boolean hasCertificateFiles(Path trustedCerts) {
        try (Stream<Path> stream = Files.list(trustedCerts)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(Files::isReadable)
                    .anyMatch(
                            p -> {
                                String name = p.getFileName().toString().toLowerCase();
                                return name.endsWith(".der")
                                        || name.endsWith(".cer")
                                        || name.endsWith(".crt")
                                        || name.endsWith(".pem");
                            });
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Cannot list trusted/certs under " + trustedCerts + ": " + e.getMessage(), e);
        }
    }

    private static X509Certificate readCertificate(Path path) throws Exception {
        try (InputStream in = Files.newInputStream(path)) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
    }

    private static PrivateKey readPrivateKey(Path path) throws Exception {
        String pem = Files.readString(path, StandardCharsets.US_ASCII);
        String normalized =
                pem.replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replaceAll("\\s", "");
        if (normalized.isBlank()) {
            throw new IllegalStateException("private-key.pem is empty: " + path);
        }
        byte[] der = Base64.getDecoder().decode(normalized);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }
}
