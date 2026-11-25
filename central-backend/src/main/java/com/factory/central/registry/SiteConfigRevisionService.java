package com.factory.central.registry;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Bumps {@code site_config_revisions} in the caller's transaction. Seed inserts use
 * {@code ON CONFLICT DO NOTHING}; mutations use the self-healing upsert {@code +1}.
 * Flushes the persistence context first so JDBC sees sites just saved via JPA.
 */
@Service
public class SiteConfigRevisionService {

    private static final String SEED_SQL =
            """
            INSERT INTO site_config_revisions (site_id, version, updated_at)
            VALUES (?, 1, now())
            ON CONFLICT (site_id) DO NOTHING
            """;

    private static final String BUMP_SQL =
            """
            INSERT INTO site_config_revisions (site_id, version, updated_at)
            VALUES (?, 2, now())
            ON CONFLICT (site_id) DO UPDATE
            SET version = site_config_revisions.version + 1,
                updated_at = EXCLUDED.updated_at
            """;

    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

    public SiteConfigRevisionService(JdbcTemplate jdbcTemplate, EntityManager entityManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.entityManager = entityManager;
    }

    /** Insert version 1 if missing; never bumps an existing row. */
    public void seedIfAbsent(String siteId) {
        entityManager.flush();
        jdbcTemplate.update(SEED_SQL, siteId);
    }

    /** Self-healing upsert that increments version (or inserts 2 when the row was missing). */
    public void bump(String siteId) {
        entityManager.flush();
        jdbcTemplate.update(BUMP_SQL, siteId);
    }

    /** Lexicographically sorted bumps so multi-site batches cannot deadlock. */
    public void bumpAll(Collection<String> siteIds) {
        if (siteIds == null || siteIds.isEmpty()) {
            return;
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String siteId : siteIds) {
            if (siteId != null && !siteId.isBlank()) {
                unique.add(siteId);
            }
        }
        List<String> ordered = new ArrayList<>(unique);
        ordered.sort(Comparator.naturalOrder());
        entityManager.flush();
        for (String siteId : ordered) {
            jdbcTemplate.update(BUMP_SQL, siteId);
        }
    }
}
