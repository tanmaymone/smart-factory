package com.factory.central.registry;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * JDBC REPEATABLE READ projection for {@code GET /api/edge/{siteId}/config}.
 * Site existence and version are resolved before tag_mappings.
 */
@Service
public class EdgeConfigManifestService {

    static final Pattern SITE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$");

    private static final String SITE_REVISION_SQL =
            """
            SELECT s.id AS site_id, s.lifecycle AS site_lifecycle, r.version AS revision
            FROM sites s
            LEFT JOIN site_config_revisions r ON r.site_id = s.id
            WHERE s.id = ?
            """;

    private static final String LIVE_TAGS_SQL =
            """
            SELECT l.id AS line_id,
                   t.tag_code,
                   t.protocol,
                   t.external_address,
                   t.data_type,
                   t.writable,
                   t.deadband,
                   t.endpoint_url
            FROM lines l
            INNER JOIN tag_mappings t
                ON t.site_id = l.site_id AND t.line_id = l.id
            WHERE l.site_id = ?
              AND l.lifecycle = 'ACTIVE'
              AND t.lifecycle = 'ACTIVE'
              AND t.protocol = 'OPC_UA'
            ORDER BY l.id ASC, t.tag_code ASC
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate repeatableReadTx;

    public EdgeConfigManifestService(JdbcTemplate jdbcTemplate, PlatformTransactionManager txManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.repeatableReadTx = new TransactionTemplate(txManager);
        this.repeatableReadTx.setReadOnly(true);
        this.repeatableReadTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.repeatableReadTx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public String requireValidSiteId(String rawSiteId) {
        if (rawSiteId == null || rawSiteId.isBlank()) {
            throw invalidSiteId(rawSiteId);
        }
        String siteId = rawSiteId.trim();
        if (!SITE_ID_PATTERN.matcher(siteId).matches()) {
            throw invalidSiteId(rawSiteId);
        }
        return siteId;
    }

    /** Proves site exists and returns version (missing revision → 1). */
    public long resolveVersion(String siteId) {
        return repeatableReadTx.execute(status -> loadSiteRevision(siteId).version());
    }

    public EdgeConfigManifest buildManifest(String siteId) {
        return repeatableReadTx.execute(status -> {
            SiteRevision site = loadSiteRevision(siteId);
            List<EdgeConfigLine> lines;
            if (!"ACTIVE".equals(site.lifecycle())) {
                lines = List.of();
            } else {
                lines = loadLiveLines(siteId);
            }
            String contentHash = ManifestCanonicalJson.hashLines(lines);
            return new EdgeConfigManifest(
                    site.siteId(),
                    site.lifecycle(),
                    site.version(),
                    contentHash,
                    Instant.now(),
                    lines);
        });
    }

    private SiteRevision loadSiteRevision(String siteId) {
        List<SiteRevision> rows = jdbcTemplate.query(SITE_REVISION_SQL, (rs, rowNum) -> mapSite(rs), siteId);
        if (rows.isEmpty()) {
            throw new RegistryException(
                    RegistryErrorCode.SITE_NOT_FOUND, "Site not found: " + siteId, HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    private List<EdgeConfigLine> loadLiveLines(String siteId) {
        Map<String, List<EdgeConfigTag>> byLine = new LinkedHashMap<>();
        jdbcTemplate.query(LIVE_TAGS_SQL, rs -> {
            String lineId = rs.getString("line_id");
            EdgeConfigTag tag = new EdgeConfigTag(
                    rs.getString("tag_code"),
                    rs.getString("protocol"),
                    rs.getString("external_address"),
                    rs.getString("data_type"),
                    rs.getBoolean("writable"),
                    rs.getDouble("deadband"),
                    rs.getString("endpoint_url"));
            byLine.computeIfAbsent(lineId, ignored -> new ArrayList<>()).add(tag);
        }, siteId);
        List<EdgeConfigLine> lines = new ArrayList<>(byLine.size());
        for (Map.Entry<String, List<EdgeConfigTag>> entry : byLine.entrySet()) {
            lines.add(new EdgeConfigLine(entry.getKey(), List.copyOf(entry.getValue())));
        }
        return List.copyOf(lines);
    }

    private static SiteRevision mapSite(ResultSet rs) throws SQLException {
        long version = Optional.ofNullable(rs.getObject("revision"))
                .map(v -> ((Number) v).longValue())
                .orElse(1L);
        return new SiteRevision(rs.getString("site_id"), rs.getString("site_lifecycle"), version);
    }

    private static RegistryException invalidSiteId(String raw) {
        return new RegistryException(
                RegistryErrorCode.INVALID_PARAMETER,
                "Invalid siteId: " + raw,
                HttpStatus.BAD_REQUEST);
    }

    private record SiteRevision(String siteId, String lifecycle, long version) {}
}
