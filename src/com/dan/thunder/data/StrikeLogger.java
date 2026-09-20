package com.dan.thunder.data;

import com.dan.thunder.model.Mood;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Schreibt Blitze gesammelt (alle 3 s) nach TH_STRIKE. Läuft in einem eigenen Thread, damit die
 * Animation nie auf die Datenbank wartet. Nach einem Fehler schaltet es sich ab und meldet das.
 */
public final class StrikeLogger implements AutoCloseable {

    /** Eine Zeile für TH_STRIKE. */
    public record Entry(long epochMs, int levelId, Mood mood, String kind, boolean clicked, double distKm,
                        Integer peakKa, double posX, long seed) { }

    private static final String SQL = "INSERT INTO TH_STRIKE (STRUCK_AT, LEVEL_ID, MOOD, KIND, ORIGIN, DIST_KM, "
            + "PEAK_KA, POS_X, SEED) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private final DbConfig config;
    private final Consumer<String> status;
    private final LinkedBlockingQueue<Entry> queue = new LinkedBlockingQueue<>();
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "thunder-strike-log");
        t.setDaemon(true);
        return t;
    });
    private Connection con;
    private volatile boolean failed;
    private long written;

    public StrikeLogger(DbConfig config, Consumer<String> status) {
        this.config = config;
        this.status = status;
        exec.scheduleWithFixedDelay(this::flush, 3, 3, TimeUnit.SECONDS);
    }

    public void log(Entry e) {
        if (!failed) {
            queue.offer(e);
        }
    }

    public long written() {
        return written;
    }

    private void flush() {
        if (failed || queue.isEmpty()) {
            return;
        }
        List<Entry> batch = new ArrayList<>();
        queue.drainTo(batch);
        try {
            if (con == null || con.isClosed()) {
                DriverManager.setLoginTimeout(config.timeoutSeconds());
                con = DriverManager.getConnection(config.url(), config.user(), config.password());
                con.setAutoCommit(false);
            }
            try (PreparedStatement ps = con.prepareStatement(SQL)) {
                for (Entry e : batch) {
                    ps.setTimestamp(1, new Timestamp(e.epochMs()));
                    if (e.levelId() > 0) {
                        ps.setInt(2, e.levelId());
                    } else {
                        ps.setNull(2, Types.NUMERIC);
                    }
                    ps.setString(3, e.mood().name());
                    ps.setString(4, e.kind());
                    ps.setString(5, e.clicked() ? "KLICK" : "AUTO");
                    ps.setDouble(6, Math.round(e.distKm() * 100) / 100.0);
                    if (e.peakKa() == null) {
                        ps.setNull(7, Types.NUMERIC);
                    } else {
                        ps.setInt(7, e.peakKa());
                    }
                    ps.setDouble(8, Math.round(Math.max(-9, Math.min(9, e.posX())) * 10000) / 10000.0);
                    ps.setLong(9, e.seed());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            con.commit();
            written += batch.size();
        } catch (SQLException ex) {
            failed = true;
            queue.clear();
            closeQuietly();
            status.accept("Blitzprotokoll aus (" + ex.getMessage().replaceAll("\\s+", " ").trim() + ")");
        }
    }

    private void closeQuietly() {
        try {
            if (con != null) {
                con.close();
            }
        } catch (SQLException ignored) {
            // egal
        }
        con = null;
    }

    @Override
    public void close() {
        exec.shutdown();
        try {
            exec.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        flush();
        closeQuietly();
    }
}
