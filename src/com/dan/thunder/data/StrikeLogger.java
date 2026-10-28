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

    /**
     * Eine Zeile für TH_STRIKE; seit Phase 18c mit dem Ort in Metern (Einschlag bzw. Mitte der Entladung, x Ost, z Süd
     * vom Hauptblick) und der Art des Blitzes in {@code com.dan.lightning} (CLOUD_GROUND, INTRA_CLOUD, SPIDER, SHEET).
     */
    public record Entry(long epochMs, int levelId, Mood mood, String kind, boolean clicked, double distKm,
                        Integer peakKa, double posX, long seed, Double xM, Double zM, String boltKind) {
        public Entry(long epochMs, int levelId, Mood mood, String kind, boolean clicked, double distKm, Integer peakKa, double posX, long seed) {
            this(epochMs, levelId, mood, kind, clicked, distKm, peakKa, posX, seed, null, null, null);
        }
    }

    private static final String SQL = "INSERT INTO TH_STRIKE (STRUCK_AT, LEVEL_ID, MOOD, KIND, ORIGIN, DIST_KM, "
            + "PEAK_KA, POS_X, SEED) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    /** Seit Phase 18c (nach sql/04_th_3d.sql). */
    private static final String SQL_3D = "INSERT INTO TH_STRIKE (STRUCK_AT, LEVEL_ID, MOOD, KIND, ORIGIN, DIST_KM, "
            + "PEAK_KA, POS_X, SEED, X_M, Z_M, BOLT_KIND) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private Boolean mit3d;

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

    public boolean failed() { return failed; }

    /** „mit Ort in Metern“, „ohne Ort (sql/04_th_3d.sql fehlt)“ oder „noch nicht verbunden“. */
    public String art() { Boolean m = mit3d; return m == null ? "noch nicht verbunden" : m ? "mit Ort in Metern" : "ohne Ort (sql/04_th_3d.sql fehlt)"; }

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
            if (mit3d == null) {
                try (PreparedStatement q = con.prepareStatement("SELECT COUNT(*) FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TH_STRIKE' AND COLUMN_NAME IN ('X_M', 'Z_M', 'BOLT_KIND')");
                     java.sql.ResultSet rs = q.executeQuery()) {
                    mit3d = rs.next() && rs.getInt(1) == 3;
                }
            }
            try (PreparedStatement ps = con.prepareStatement(mit3d ? SQL_3D : SQL)) {
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
                    if (mit3d) {
                        if (e.xM() == null) ps.setNull(10, Types.NUMERIC); else ps.setDouble(10, Math.round(e.xM() * 10) / 10.0);
                        if (e.zM() == null) ps.setNull(11, Types.NUMERIC); else ps.setDouble(11, Math.round(e.zM() * 10) / 10.0);
                        if (e.boltKind() == null) ps.setNull(12, Types.VARCHAR); else ps.setString(12, e.boltKind());
                    }
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
