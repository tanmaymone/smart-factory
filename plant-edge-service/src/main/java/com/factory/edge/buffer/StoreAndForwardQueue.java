package com.factory.edge.buffer;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * SQLite WAL store-and-forward queue. Fast-path and paced worker share {@code inflightSeq}; publish
 * always runs outside the lock; rows are deleted only after {@link TelemetryLink#publish} succeeds.
 */
@Component
public class StoreAndForwardQueue implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(StoreAndForwardQueue.class);

    private final TelemetryLink link;
    private final int maxRecords;
    private final TelemetryRecordCodec codec;
    private final Object lock = new Object();
    private final Connection connection;
    private final PacedOutboundDrainer drainer;

    private Long inflightSeq;

    @Autowired
    public StoreAndForwardQueue(
            TelemetryLink link, BufferProperties properties, ObjectMapper objectMapper) {
        this(
                link,
                Path.of(properties.getPath()).toAbsolutePath().normalize(),
                properties.getMaxRecords(),
                properties.getDrainBatchSize(),
                properties.getDrainPauseMs(),
                objectMapper,
                true);
    }

    /** Package-visible for tests that need an explicit path/max without Spring. */
    StoreAndForwardQueue(TelemetryLink link, Path dbPath, int maxRecords, ObjectMapper objectMapper) {
        this(link, dbPath, maxRecords, 32, 10L, objectMapper, true);
    }

    StoreAndForwardQueue(
            TelemetryLink link,
            Path dbPath,
            int maxRecords,
            int drainBatchSize,
            long drainPauseMs,
            ObjectMapper objectMapper) {
        this(link, dbPath, maxRecords, drainBatchSize, drainPauseMs, objectMapper, true);
    }

    private StoreAndForwardQueue(
            TelemetryLink link,
            Path dbPath,
            int maxRecords,
            int drainBatchSize,
            long drainPauseMs,
            ObjectMapper objectMapper,
            boolean startDrainer) {
        this.link = link;
        this.maxRecords = Math.max(1, maxRecords);
        this.codec = new TelemetryRecordCodec(objectMapper);
        try {
            Files.createDirectories(dbPath.getParent());
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute(
                        """
                        CREATE TABLE IF NOT EXISTS outbound_queue (
                          seq INTEGER PRIMARY KEY AUTOINCREMENT,
                          payload TEXT NOT NULL,
                          enqueued_at_ms INTEGER NOT NULL
                        )
                        """);
            }
            int residual = depthUnlocked();
            log.info("EDGE buffer open path={} residualDepth={}", dbPath.toAbsolutePath(), residual);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to open EDGE buffer at " + dbPath + ": " + e.getMessage(), e);
        }

        this.drainer =
                startDrainer
                        ? new PacedOutboundDrainer(
                                "edge-buffer-drain", drainBatchSize, drainPauseMs, this::drainBatch)
                        : null;
        link.bindRecovery(this::onLinkRecovered);
        if (link.isAvailable() && depth() > 0) {
            kickDrain();
        }
    }

    public void offer(TelemetryRecord record) {
        Long claimSeq = null;
        boolean kickDrain = false;
        synchronized (lock) {
            if (depthUnlocked() > 0 || inflightSeq != null || !link.isAvailable()) {
                enqueueUnlocked(record);
                kickDrain = inflightSeq == null && link.isAvailable();
            } else {
                claimSeq = enqueueUnlocked(record);
                inflightSeq = claimSeq;
            }
        }

        if (claimSeq != null) {
            publishClaimed(claimSeq, record, true);
            return;
        }
        if (kickDrain) {
            kickDrain();
        }
    }

    public void onLinkRecovered() {
        kickDrain();
    }

    public int depth() {
        synchronized (lock) {
            return depthUnlocked();
        }
    }

    private void kickDrain() {
        if (drainer != null) {
            drainer.kick();
        }
    }

    private PacedOutboundDrainer.DrainResult drainBatch(int maxPublishes) {
        for (int i = 0; i < maxPublishes; i++) {
            QueuedRow head;
            synchronized (lock) {
                if (inflightSeq != null) {
                    return PacedOutboundDrainer.DrainResult.IDLE;
                }
                if (!link.isAvailable()) {
                    return PacedOutboundDrainer.DrainResult.IDLE;
                }
                Optional<QueuedRow> next = peekMinUnlocked();
                if (next.isEmpty()) {
                    return PacedOutboundDrainer.DrainResult.IDLE;
                }
                head = next.get();
                inflightSeq = head.seq();
            }

            if (!publishClaimed(head.seq(), head.record(), false)) {
                return PacedOutboundDrainer.DrainResult.IDLE;
            }
        }
        synchronized (lock) {
            if (depthUnlocked() > 0 && link.isAvailable() && inflightSeq == null) {
                return PacedOutboundDrainer.DrainResult.MORE;
            }
            return PacedOutboundDrainer.DrainResult.IDLE;
        }
    }

    /**
     * @param kickOnFailure when true (fast path), kick the worker after a failed publish so recovery
     *     can retry
     * @return true if publish succeeded
     */
    private boolean publishClaimed(long seq, TelemetryRecord record, boolean kickOnFailure) {
        try {
            link.publish(record);
            synchronized (lock) {
                try {
                    deleteUnlocked(seq);
                } catch (SQLException e) {
                    throw new IllegalStateException("EDGE buffer delete failed: " + e.getMessage(), e);
                }
                if (Long.valueOf(seq).equals(inflightSeq)) {
                    inflightSeq = null;
                }
                boolean more = depthUnlocked() > 0 && link.isAvailable();
                if (more) {
                    kickDrain();
                }
            }
            return true;
        } catch (LinkUnavailableException e) {
            synchronized (lock) {
                if (Long.valueOf(seq).equals(inflightSeq)) {
                    inflightSeq = null;
                }
            }
            if (kickOnFailure) {
                kickDrain();
            }
            return false;
        }
    }

    private long enqueueUnlocked(TelemetryRecord record) {
        try {
            while (depthUnlocked() >= maxRecords) {
                evictOldestWaitingUnlocked();
            }
            try (PreparedStatement ps =
                    connection.prepareStatement(
                            "INSERT INTO outbound_queue(payload, enqueued_at_ms) VALUES (?, ?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, codec.encode(record));
                ps.setLong(2, System.currentTimeMillis());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new IllegalStateException("EDGE buffer insert returned no seq");
                    }
                    return keys.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("EDGE buffer enqueue failed: " + e.getMessage(), e);
        }
    }

    private void evictOldestWaitingUnlocked() throws SQLException {
        Long victim;
        if (inflightSeq == null) {
            victim = minSeqUnlocked(null);
        } else {
            victim = minSeqUnlocked(inflightSeq);
        }
        if (victim == null) {
            throw new IllegalStateException(
                    "EDGE buffer full but no evictable row (inflightSeq=" + inflightSeq + ")");
        }
        int depth = depthUnlocked();
        deleteUnlocked(victim);
        log.warn(
                "EDGE buffer eviction seq={} depth={} max={}; oldest telemetry dropped",
                victim,
                depth,
                maxRecords);
    }

    private Optional<QueuedRow> peekMinUnlocked() {
        try (PreparedStatement ps =
                        connection.prepareStatement(
                                "SELECT seq, payload FROM outbound_queue ORDER BY seq ASC LIMIT 1");
                ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return Optional.empty();
            }
            long seq = rs.getLong("seq");
            TelemetryRecord record = codec.decode(rs.getString("payload"));
            return Optional.of(new QueuedRow(seq, record));
        } catch (SQLException e) {
            throw new IllegalStateException("EDGE buffer peek failed: " + e.getMessage(), e);
        }
    }

    private Long minSeqUnlocked(Long strictlyGreaterThan) throws SQLException {
        String sql =
                strictlyGreaterThan == null
                        ? "SELECT MIN(seq) FROM outbound_queue"
                        : "SELECT MIN(seq) FROM outbound_queue WHERE seq > ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (strictlyGreaterThan != null) {
                ps.setLong(1, strictlyGreaterThan);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                long value = rs.getLong(1);
                return rs.wasNull() ? null : value;
            }
        }
    }

    private void deleteUnlocked(long seq) throws SQLException {
        try (PreparedStatement ps =
                connection.prepareStatement("DELETE FROM outbound_queue WHERE seq = ?")) {
            ps.setLong(1, seq);
            ps.executeUpdate();
        }
    }

    private int depthUnlocked() {
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM outbound_queue")) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException("EDGE buffer depth failed: " + e.getMessage(), e);
        }
    }

    @PreDestroy
    @Override
    public void close() {
        if (drainer != null) {
            drainer.close();
        }
        synchronized (lock) {
            try {
                connection.close();
            } catch (SQLException e) {
                log.debug("EDGE buffer close: {}", e.toString());
            }
        }
    }

    private record QueuedRow(long seq, TelemetryRecord record) {}
}
