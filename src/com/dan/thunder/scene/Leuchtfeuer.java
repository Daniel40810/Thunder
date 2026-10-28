package com.dan.thunder.scene;

import com.dan.beacon.Beacon;
import com.dan.beacon.Scatter;
import com.dan.beacon.ScatterGrid;

import java.util.Locale;

/**
 * Das Leuchtfeuer auf der Landzunge (Sylt Phase 19, F60 A; nach Sylt Phase 10): ein Drehfeuer aus
 * {@code com.dan.beacon}, Fl(3) W 15s mit drei Ringlinsen ({@link Beacon#flash3()}), Brennpunkt in der Laterne aus
 * {@link Bauwerke}. Sein Strahl kreist durch den Regen: Das Streugitter bekommt die Regenmenge am Turm, der Dunst kommt
 * aus der Sichtweite. Der Dämmerungsschalter folgt dem Licht am Turm (an unter 150 lx); im Gewitter brennt es immer.
 */
public final class Leuchtfeuer {
    public final Beacon feuer = Beacon.flash3();
    public final ScatterGrid grid;
    /** Feuer zeigen und Strahl zeigen. */
    public volatile boolean an = true, strahl = true;
    private double regenGefuellt = -1, lux;

    public Leuchtfeuer(double[] fokus, Beacon.Quality q) {
        feuer.name = "Leuchtturm auf der Landzunge";
        feuer.quality = q;
        feuer.automatic = true;
        feuer.x = fokus[0];
        feuer.y = fokus[1];
        feuer.z = fokus[2];
        grid = new ScatterGrid(q.gridAz, q.gridEl, q.gridR);
        feuer.setSectors(feuer.sectors());
    }

    /** Streukoeffizient des Regens (1/m) nach der Regenmenge in mm/h (wie Sylt). */
    public static double regenSigma(double mmh) { return mmh <= 0 ? 0 : 0.25 * Math.pow(mmh, 0.63) / 1000; }

    /** Weiter um dt: Umgebungslicht (lx), Sichtweite (km), Regen am Turm (mm/h). */
    public void schritt(double dt, double umgebungLux, double sichtKm, double regen) {
        lux = umgebungLux;
        feuer.ambientLux = umgebungLux;
        feuer.hazePerM = 2.996 / (Math.max(0.5, sichtKm) * 1000);
        if (Math.abs(regen - regenGefuellt) > 0.05) {
            grid.fill(Scatter.uniform(regenSigma(regen)), feuer.x, feuer.y, feuer.z);
            regenGefuellt = regen;
        }
        feuer.step(dt);
    }

    /** Für Standbilder: Schalter sofort nach dem Licht stellen. */
    public void sofort(double umgebungLux) { feuer.snapOn(umgebungLux < feuer.onLux); }

    /** Umgebungslicht in lx aus Sonne und Himmel (Szeneneinheiten von com.dan.sky). */
    public static double lux(double[] sonne, double sonneHoch, double[] himmel) {
        double l = 0.2126 * himmel[0] + 0.7152 * himmel[1] + 0.0722 * himmel[2];
        double s = 0.2126 * sonne[0] + 0.7152 * sonne[1] + 0.0722 * sonne[2];
        return (l + s * Math.max(0, sonneHoch)) * com.dan.sky.Sky.CD_PER_UNIT;
    }

    public String describe() {
        return String.format(Locale.GERMAN, "Leuchtfeuer %s %s · Licht am Turm %s lx", feuer.characteristic().text, feuer.isOn() ? "an" : "aus",
                lux < 10 ? String.format(Locale.GERMAN, "%.2g", lux) : String.format(Locale.GERMAN, "%,.0f", lux));
    }
}
