package com.factory.central.ingestion;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TelemetryHistoryRepository {

    private static final String INSERT_SQL =
            """
            INSERT INTO telemetry_history
                (time, site_id, line_id, tag_code, numeric_value, boolean_value, string_value, quality)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_SQL =
            """
            SELECT time, site_id, line_id, tag_code, numeric_value, boolean_value, string_value, quality
            FROM telemetry_history
            WHERE site_id = ?
              AND line_id = ?
              AND tag_code = ?
              AND time >= ?
              AND time < ?
            ORDER BY time DESC
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public TelemetryHistoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void batchInsert(List<TelemetryHistoryRow> rows) {
        if (rows.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(INSERT_SQL, rows, rows.size(), (ps, row) -> {
            ps.setObject(1, OffsetDateTime.ofInstant(row.time(), ZoneOffset.UTC));
            ps.setString(2, row.siteId());
            ps.setString(3, row.lineId());
            ps.setString(4, row.tagCode());
            if (row.numericValue() == null) {
                ps.setObject(5, null);
            } else {
                ps.setDouble(5, row.numericValue());
            }
            if (row.booleanValue() == null) {
                ps.setObject(6, null);
            } else {
                ps.setBoolean(6, row.booleanValue());
            }
            ps.setString(7, row.stringValue());
            ps.setString(8, row.quality());
        });
    }

    public List<TelemetryHistoryRow> findHistory(
            String siteId, String lineId, String tagCode, Instant from, Instant to, int limit) {
        return jdbcTemplate.query(
                SELECT_SQL,
                (rs, rowNum) -> {
                    Timestamp ts = rs.getTimestamp("time");
                    Instant time = ts.toInstant();
                    Double numeric = (Double) rs.getObject("numeric_value");
                    Boolean bool = (Boolean) rs.getObject("boolean_value");
                    return new TelemetryHistoryRow(
                            time,
                            rs.getString("site_id"),
                            rs.getString("line_id"),
                            rs.getString("tag_code"),
                            numeric,
                            bool,
                            rs.getString("string_value"),
                            rs.getString("quality"));
                },
                siteId,
                lineId,
                tagCode,
                OffsetDateTime.ofInstant(from, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(to, ZoneOffset.UTC),
                limit);
    }
}
