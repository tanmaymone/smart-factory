package com.factory.edge.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EdgeConfigSnapshotStore {

    private static final Logger log = LoggerFactory.getLogger(EdgeConfigSnapshotStore.class);

    private final EdgeConfigProperties properties;
    private final ObjectMapper objectMapper;

    public EdgeConfigSnapshotStore(EdgeConfigProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Optional<LoadedSnapshot> read() {
        Path path = Path.of(properties.getSnapshotPath());
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            String envelopeJson = Files.readString(path, StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(envelopeJson);
            String siteId = root.path("siteId").asText("");
            String manifestJson = root.path("manifestJson").asText(null);
            String sha256 = root.path("sha256").asText("");
            String savedAt = root.path("savedAt").asText(null);
            if (manifestJson == null || manifestJson.isBlank()) {
                quarantine(path);
                return Optional.empty();
            }
            String actual = sha256Hex(manifestJson);
            if (!actual.equalsIgnoreCase(sha256)) {
                log.warn("EDGE CONFIG snapshot checksum mismatch; treating as absent");
                quarantine(path);
                return Optional.empty();
            }
            if (!properties.getNormalizedSiteId().equals(siteId.trim())) {
                log.warn(
                        "EDGE CONFIG snapshot siteId '{}' != configured '{}'; treating as absent",
                        siteId,
                        properties.getNormalizedSiteId());
                return Optional.empty();
            }
            JsonNode manifestRoot = objectMapper.readTree(manifestJson);
            EdgeManifest manifest = EdgeManifest.parse(manifestJson, manifestRoot);
            Instant saved = savedAt == null || savedAt.isBlank() ? null : Instant.parse(savedAt);
            return Optional.of(new LoadedSnapshot(manifest, saved));
        } catch (Exception ex) {
            log.warn("EDGE CONFIG snapshot read failed: {}", ex.getMessage());
            quarantine(path);
            return Optional.empty();
        }
    }

    public void write(EdgeManifest manifest) throws IOException {
        validate(manifest);
        Path path = Path.of(properties.getSnapshotPath());
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        String sha = sha256Hex(manifest.rawJson());
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("schemaVersion", 1);
        envelope.put("siteId", properties.getNormalizedSiteId());
        envelope.put("savedAt", Instant.now().toString());
        envelope.put("sha256", sha);
        envelope.put("manifestJson", manifest.rawJson());
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(envelope);
        Files.write(tmp, bytes);
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private void validate(EdgeManifest manifest) {
        if (manifest.version() < 1) {
            throw new IllegalArgumentException("manifest version must be >= 1");
        }
        if (manifest.contentHash() == null || manifest.contentHash().isBlank()) {
            throw new IllegalArgumentException("manifest contentHash is required");
        }
        if (manifest.rawJson() == null || manifest.rawJson().isBlank()) {
            throw new IllegalArgumentException("manifest rawJson is required");
        }
        if (!properties.getNormalizedSiteId().equals(manifest.siteId().trim())) {
            throw new IllegalArgumentException(
                    "manifest siteId '"
                            + manifest.siteId()
                            + "' does not match configured '"
                            + properties.getNormalizedSiteId()
                            + "'");
        }
    }

    private void quarantine(Path path) {
        try {
            Path bad = path.resolveSibling(path.getFileName() + ".bad");
            Files.move(path, bad, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // leave in place; next read will retry
        }
    }

    static String sha256Hex(String utf8) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(utf8.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public record LoadedSnapshot(EdgeManifest manifest, Instant savedAt) {}
}
