package com.dan.thunder.scene;

import com.dan.cloud.Clouds;
import com.dan.lightning.Bolt;
import com.dan.lightning.Lightning;
import com.dan.rain.Rain;
import com.dan.thunder.model.Mood;
import com.dan.thunder.model.Palette;
import com.dan.thunder.model.PaletteRole;
import com.dan.thunder.model.Scene;
import com.dan.thunder.model.StormLevel;
import com.dan.thunder.storm.Stage;
import com.dan.thunder.storm.StormDirector;
import com.dan.thunder.storm.StormListener;
import com.dan.thunder.storm.Strike;
import com.dan.thunder.storm.StrikeKind;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Das Gewitter in 3D (Phase 18b, F49 A, F50 A): der {@link StormDirector} aus dem 2D-Thunder bleibt die Regie. Er
 * entscheidet nach der Stufe aus {@code TH_STORM_LEVEL}, wann ein Blitz kommt, welcher Art er ist und wie weit weg. Diese
 * Klasse setzt seine Entscheidung in den Raum: Die Stelle im Bild (Anteil der Breite) wird eine Peilung im Blickfeld des
 * Hauptblicks, die Entfernung bleibt. {@code com.dan.lightning} baut und zeichnet den Blitz:
 * <ul>
 * <li><b>Wolke – Erde</b> ({@code CG}): ein Erdblitz ({@code CLOUD_GROUND}) auf die See, den Strand oder in den
 * Leuchtturm, wenn er in seinem Fangradius einschlägt.</li>
 * <li><b>In der Wolke</b> ({@code IC}): bis 9 km ein Wolkenblitz ({@code INTRA_CLOUD}), gut die Hälfte davon als
 * Spinnenblitz unter der Untergrenze ({@code SPIDER}); weiter weg Wetterleuchten ({@code SHEET}).</li>
 * <li><b>Blitzkrone</b> ({@code KRONE}): 4 bis 7 Spinnenblitze, die rings um den Glutkern unter der Wolke
 * auseinanderlaufen, kurz nacheinander.</li>
 * </ul>
 * Der <b>Glutkern</b> ist ein schwaches, atmendes Licht in einer Zelle der Gewitterwolke, vorn rechts über der See (Ort
 * aus {@code TH_SCENE}: EMBER_X 0,62, EMBER_Y 0,33), Farbe EMBER aus {@code TH_PALETTE}, Stärke EMBER der Stufe. Er
 * leuchtet über die Lichtpunkte der Wolke ({@code CloudGl.Light.flashPos}), {@code com.dan.cloud} bleibt unverändert.
 * <p>
 * „Sanfte Blitze“ dämpft das Licht: ein Drittel der Lichtstärke, nur ein Schlag (die Regie), die Belichtung gibt
 * weniger nach.
 */
public final class Gewitter implements StormListener {
    /** Blickfeld des Hauptblicks waagerecht (Grad): 50° senkrecht im Format 16:9. */
    public static final double HFOV = 2 * Math.toDegrees(Math.atan(Math.tan(Math.toRadians(25)) * 16 / 9.0));
    /**
     * Glutkern: Abstand (m), Höhe (m), Perioden des Atmens (s) und Helligkeit. Er ist ein Bild, kein Messwert: seine
     * Leuchtdichte in der Wolke steht im festen Verhältnis zur Anpassung des Auges ({@link #glutBezug}), GLUT_FAKTOR mal
     * so hell bei EMBER 1, damit er abends und nachts gleich glimmt wie in den Bildern des 2D-Thunder [G].
     */
    public static final double GLUT_ABSTAND = 5000, GLUT_HOEHE = 1100, ATEM_A = 3.7, ATEM_B = 6.1, GLUT_FAKTOR = 8.0;
    /** Leuchtdichte je cd mitten in der Wolke ({@code composite.frag} von com.dan.cloud: 3 / (4π · 800²)). */
    static final double GLUT_JE_CD = 3 / (4 * Math.PI * 640_000);
    /** Leuchtdichte, an die das Auge angepasst ist (cd/m²), vom Renderer je Bild. */
    public volatile double glutBezug = 1;
    /** Glutkern jetzt: Vielfaches der Anpassung (HUD). */
    public volatile double glutJetzt;
    /**
     * Lichtstärke je m Kanal bei 30 kA (cd/m): das Dreifache der Vorgabe des Pakets (5000), am oberen Rand der Messungen,
     * damit ein naher Blitz in der Dämmerung blendet wie in den Bildern des 2D-Thunder [G].
     */
    public static final double KANAL_CD = 1.5e4;
    /** Sanfte Blitze: Anteil der Lichtstärke. */
    public static final double SANFT = 1 / 3.0;

    public final Lightning blitz = new Lightning();
    public final Rain regen = new Rain();
    private final StormDirector regie;
    private final Random rnd = new Random(2026);
    /** Regie an (App) oder aus (Standbilder: Blitze von Hand). */
    public volatile boolean regieAn = true, regenAn = true, blitzeAn = true;
    private volatile boolean sanft;
    private volatile StormLevel level;
    private volatile Mood mood = Mood.ABEND;
    private volatile Palette palette;
    private volatile Scene scene;
    private double time;
    /** Glutkern: Ort {x, y, z} und Lichtstärke jetzt (cd, RGB). */
    public volatile double[] glutOrt = glutOrt(0.62, 0.33), glutRgb = {0, 0, 0};
    /** Was zuletzt kam (HUD). */
    public volatile String zuletzt = "";
    public volatile int kronen, erdblitze, wolkenblitze, wetterleuchten;
    private final List<double[]> krone = new ArrayList<>();
    private double kroneT;

    public Gewitter() {
        regie = new StormDirector(null);
        regie.addListener(this);
        blitz.seed = 2026;
        blitz.setLevel(Lightning.Level.OFF);   // die Regie entscheidet, nicht die Rate des Pakets
        blitz.ground = Gelaende::height;
        blitz.maxBolts = 10;
        blitz.attractors.add(new Lightning.Attractor(Site.LEUCHTTURM[0], Site.FEUER_HOEHE + 2.5, Site.LEUCHTTURM[1], "Leuchtturm"));
        regen.seed = 2026;
        regen.rate = 0;
    }

    public StormDirector regie() { return regie; }

    public void setLevel(StormLevel l) {
        level = l;
        if (l != null) regie.setLevel(l, time * 1000);
    }

    public StormLevel level() { return level; }

    public void setSanft(boolean s) {
        sanft = s;
        regie.setSoft(s);
    }

    public boolean sanft() { return sanft; }

    /** Daten aus der Datenbank: Farben und der Ort des Glutkerns. */
    public void setDaten(Palette abend, Palette nacht, Scene sc, Mood m) {
        pAbend = abend;
        pNacht = nacht;
        scene = sc;
        mood = m;
        palette = m == Mood.NACHT ? nacht : abend;
        if (sc != null) glutOrt = glutOrt(sc.emberX(), sc.emberY());
    }

    private volatile Palette pAbend, pNacht;

    /** Stimmung (für die Farben); t 0..1 Überblendung Abend → Nacht. */
    public void setStimmung(double nacht) {
        Palette a = pAbend, n = pNacht;
        if (a != null && n != null) palette = Palette.lerp(a, n, nacht);
    }

    /** Farbe einer Rolle aus TH_PALETTE als lineares RGB mit Leuchtdichte 1, oder Weiß ohne Daten. */
    public double[] farbe(PaletteRole r) {
        Palette p = palette;
        if (p == null) return new double[]{1, 1, 1};
        Color c = p.get(r);
        double[] v = {lin(c.getRed()), lin(c.getGreen()), lin(c.getBlue())};
        double l = 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
        if (l <= 1e-6) return new double[]{1, 1, 1};
        return new double[]{v[0] / l, v[1] / l, v[2] / l};
    }

    static double lin(int c) {
        double s = c / 255.0;
        return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    /** Der Glutkern aus der Lage im 2D-Bild: Peilung aus x, Höhe aus dem Winkel über der Kimm (Kimm bei 0,60). */
    static double[] glutOrt(double ex, double ey) {
        double peil = Site.BLICK + (ex - 0.5) * HFOV;
        double[] p = Site.peilung(peil, GLUT_ABSTAND);
        return new double[]{p[0], GLUT_HOEHE, p[1]};
    }

    /** Je Bild: Wolken und Auge an das Paket, die Regie fragen, Krone abspielen, Glutkern atmen, Regen. */
    public void schritt(double dt, Clouds.Snapshot cs, boolean wolken, double[] auge, double windSpeed, double windFrom) {
        time += dt;
        futter(cs, wolken, auge);
        StormLevel l = level;
        if (regieAn && blitzeAn && l != null) regie.update(time * 1000, BUEHNE);
        // die Krone: die Spinnenblitze kommen kurz nacheinander (je 40 bis 120 ms)
        if (!krone.isEmpty() && time >= kroneT) {
            double[] k = krone.remove(0);
            blitz.discharge(Bolt.Kind.SPIDER, k[0], k[1]);
            kroneT = time + 0.04 + 0.08 * rnd.nextDouble();
        }
        blitz.candelaPerMetre = KANAL_CD * (sanft ? SANFT : 1);
        blitz.step(dt);
        // Glutkern: zwei Perioden und ein wenig Flackern, Stärke EMBER der Stufe
        double ember = l == null ? 0.55 : l.ember();
        double atem = 0.72 + 0.18 * Math.sin(2 * Math.PI * time / ATEM_A) + 0.10 * Math.sin(2 * Math.PI * time / ATEM_B + 1.3);
        glutJetzt = GLUT_FAKTOR * ember * atem * (blitzeAn ? 1 : 0);
        double cd = glutJetzt * glutBezug / GLUT_JE_CD;
        double[] f = farbe(PaletteRole.EMBER);
        glutRgb = new double[]{f[0] * cd, f[1] * cd, f[2] * cd};
        // Regen nach der Stufe
        double rate = regenAn && l != null ? regenRate(l) : 0;
        regen.rate = rate;
        regen.windSpeed = windSpeed;
        regen.windFromDeg = windFrom;
        regen.gust = 0.3;
        regen.waterLevel = 0;
        regen.cloudBase = wolken && cs != null ? cs.type().base : 700;
        regen.eyeX = auge[0];
        regen.eyeY = auge[1];
        regen.eyeZ = auge[2];
        // unter der Zelle des Unwetters regnet es überall, sonst nur unter den Regenfeldern der Wolken
        double boden = l == null ? 0 : l.code().equals("SEVERE") ? 0.6 : 0;
        if (!wolken || cs == null || rate <= 0) regen.curtain = null;
        else {
            double p = Math.max(1e-3, cs.precipitation());
            regen.curtain = (x, z) -> Math.max(boden, Math.min(1, Clouds.rain(cs, x, z) / p * 2));
        }
        regen.step(dt);
    }

    /** Regen der Stufe (mm/h) aus RAIN (Tropfen im 2D-Bild): Wetterleuchten 1,4, Gewitter 10, Unwetter 30 [G]. */
    public static double regenRate(StormLevel l) { return 30 * Math.pow(Math.max(0, l.rainDrops()) / 950.0, 1.6); }

    /** Niederschlag für die Wolken (0..1). */
    public double niederschlag() { return regen.rate > 0 ? Math.min(1, 0.35 + regen.rate / 25) : 0; }

    private void futter(Clouds.Snapshot cs, boolean wolken, double[] e) {
        blitz.cx = e[0];
        blitz.cz = e[2];
        blitz.radius = 16_000;
        blitz.listenerX = e[0];
        blitz.listenerY = e[1];
        blitz.listenerZ = e[2];
        blitz.groundLevel = 0;
        blitz.temperatureC = 19;
        if (wolken && cs != null) {
            double base = cs.type().base, top = base + cs.type().maxThickness;
            blitz.cloudBase = base;
            blitz.occlusion = (px, py, pz, ex, ey, ez) -> durchlass(cs, px, py, pz, ex, ey, ez, base, top);
        } else {
            blitz.cloudBase = 700;
            blitz.occlusion = null;
        }
        blitz.cells = null;
    }

    /** Durchlässigkeit der Wolke zwischen Kanal und Auge (aus Lighthouse Phase 10): Dichte an zehn Stellen, halbe Auslöschung. */
    static double durchlass(Clouds.Snapshot cs, double px, double py, double pz, double ex, double ey, double ez, double base, double top) {
        double dx = ex - px, dy = ey - py, dz = ez - pz, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1) return 1;
        double t0 = 0, t1 = 1;
        if (Math.abs(dy) > 1e-6) {
            double ta = (base - py) / dy, tb = (top - py) / dy;
            t0 = Math.max(0, Math.min(ta, tb));
            t1 = Math.min(1, Math.max(ta, tb));
        } else if (py < base || py > top) return 1;
        if (t1 <= t0) return 1;
        double tau = 0, step = (t1 - t0) / 10;
        for (int i = 0; i < 10; i++) {
            double t = t0 + (i + 0.5) * step;
            tau += Clouds.density(cs, px + dx * t, py + dy * t, pz + dz * t, false) * step * len;
            if (tau > 20) break;
        }
        return Math.exp(-0.5 * tau);
    }

    // ------------------------------------------------------------------ die Regie setzt die Blitze in den Raum

    /** Die Bühne des 2D-Thunder (1600 × 900, Kimm und Ufer aus TH_SCENE): nur für die Wahl von Ort und Abstand. */
    private final Stage BUEHNE = new Stage() {
        public double width() { return 1600; }
        public double height() { return 900; }
        public double horizonY() { Scene s = scene; return 900 * (s == null ? 0.6 : s.horizonY()); }
        public double shoreY(double x) { Scene s = scene; return 900 * (s == null ? 0.8 : s.shoreY()); }
        public double emberX() { Scene s = scene; return s == null ? 0.62 : s.emberX(); }
        public double emberY() { Scene s = scene; return s == null ? 0.33 : s.emberY(); }
    };

    /** Seit 18c: jeder Blitz der Regie mit dem gebauten Blitz (für das Blitzprotokoll und die Blitzkarte). */
    public interface BlitzListener { void blitz(Strike s, Bolt b); }
    public volatile BlitzListener onBlitz;

    @Override
    public void strikeStarted(Strike s) {
        if (!blitzeAn) return;
        double peil = Site.BLICK + (s.gx() / BUEHNE.width() - 0.5) * HFOV;
        Bolt b = ausloesen(s.kind(), peil, s.distKm(), rnd.nextDouble());
        BlitzListener l = onBlitz;
        if (l != null && b != null) l.blitz(s, b);
    }

    /**
     * Ein Blitz von Hand (Klick, Taste) über die Regie, damit er wie jeder andere ins Protokoll kommt (ORIGIN KLICK):
     * Art, Peilung (Grad von Nord), Abstand (km). Die Peilung wird zur Stelle auf der Bühne der Regie.
     */
    public void vonHand(StrikeKind kind, double peilung, double km) {
        if (!blitzeAn) return;
        double d = peilung - Site.BLICK;
        d = ((d + 540) % 360) - 180;
        double gx = (0.5 + d / HFOV) * BUEHNE.width();
        regie.spawn(kind, time * 1000, BUEHNE, gx, Math.max(0.3, km), true, rnd.nextLong());
    }

    /** Peilung (Grad) und Abstand (km) des Leuchtturms vom Hauptblick: Taste T schlägt dort ein. */
    public static double[] turm() {
        double[] t = Site.LEUCHTTURM;
        return new double[]{Lage.kurs(0, 0, t[0], t[1]), Math.hypot(t[0], t[1]) / 1000};
    }

    /**
     * Einen Blitz in den Raum setzen: Art, Peilung vom Hauptblick (Grad), Abstand (km), Würfel u 0..1 (Spinne oder
     * Wolkenblitz). Gibt den Blitz (bei der Krone den ersten) zurück.
     */
    public Bolt ausloesen(StrikeKind kind, double peilung, double km, double u) {
        double d = Math.max(0.3, km) * 1000;
        double[] p = Site.peilung(peilung, d);
        Bolt b;
        switch (kind) {
            case CG -> {
                // zielt die Regie (oder Taste T) auf einen hohen Punkt (unter 60 m), schlägt er dort ein
                int fang = -1;
                for (int i = 0; i < blitz.attractors.size(); i++) {
                    Lightning.Attractor at = blitz.attractors.get(i);
                    if (Math.hypot(at.x() - p[0], at.z() - p[1]) < 60) fang = i;
                }
                b = fang >= 0 ? blitz.strikeAttractor(fang) : blitz.strike(p[0], p[1]);
                erdblitze++;
            }
            case IC -> {
                Bolt.Kind k = km > 9 ? Bolt.Kind.SHEET : u < 0.55 ? Bolt.Kind.SPIDER : Bolt.Kind.INTRA_CLOUD;
                if (k == Bolt.Kind.SHEET) {
                    // Wetterleuchten liegt hinter der Kimm: weit draußen in derselben Richtung
                    double[] q = Site.peilung(peilung, Math.max(d, blitz.sheetDistance));
                    b = blitz.discharge(k, q[0], q[1]);
                    wetterleuchten++;
                } else {
                    b = blitz.discharge(k, p[0], p[1]);
                    wolkenblitze++;
                }
            }
            default -> {
                b = krone();
                kronen++;
            }
        }
        if (b != null) zuletzt = text(b);
        return b;
    }

    /** Die Krone: 4 bis 7 Spinnenblitze rings um den Fußpunkt des Glutkerns (0,5 bis 1,5 km), der erste sofort. */
    public Bolt krone() {
        double[] g = glutOrt;
        int n = 4 + rnd.nextInt(4);
        double a0 = rnd.nextDouble() * 2 * Math.PI;
        krone.clear();
        Bolt erster = null;
        for (int i = 0; i < n; i++) {
            double a = a0 + i * 2 * Math.PI / n + (rnd.nextDouble() - 0.5) * 0.6, r = 500 + 1000 * rnd.nextDouble();
            double x = g[0] + Math.cos(a) * r, z = g[2] + Math.sin(a) * r;
            if (i == 0) erster = blitz.discharge(Bolt.Kind.SPIDER, x, z);
            else krone.add(new double[]{x, z});
        }
        kroneT = time + 0.06;
        return erster;
    }

    /** Für Standbilder: die übrigen Spinnenblitze der Krone jetzt. */
    public void kroneSofort() {
        while (!krone.isEmpty()) {
            double[] k = krone.remove(0);
            blitz.discharge(Bolt.Kind.SPIDER, k[0], k[1]);
        }
    }

    /** Alle Blitze vergessen (Standbilder). */
    public void leeren() {
        blitz.clear();
        krone.clear();
    }

    /** „Erdblitz 2,4 km NO, 31 kA, 3 Schläge“ */
    String text(Bolt b) {
        double[] e = {blitz.listenerX, blitz.listenerZ};
        double d = Math.hypot(b.hitX - e[0], b.hitZ - e[1]), az = (Math.toDegrees(Math.atan2(b.hitX - e[0], -(b.hitZ - e[1]))) + 360) % 360;
        String[] dirs = {"N", "NO", "O", "SO", "S", "SW", "W", "NW"};
        String kind = switch (b.kind) {
            case CLOUD_GROUND -> b.attractor >= 0 ? "Einschlag in den " + blitz.attractors.get(b.attractor).name() : "Erdblitz";
            case INTRA_CLOUD -> "Wolkenblitz";
            case SPIDER -> "Spinnenblitz";
            case SHEET -> "Wetterleuchten";
        };
        double ka = 0;
        for (double k : b.strokeKA) ka = Math.max(ka, k);
        return String.format(Locale.GERMAN, "%s %.1f km %s, %.0f kA, %d %s", kind, d / 1000, dirs[(int) Math.round(az / 45) % 8], ka, b.strokeTime.length,
                b.kind == Bolt.Kind.CLOUD_GROUND ? (b.strokeTime.length == 1 ? "Schlag" : "Schläge") : "Pulse");
    }

    public double time() { return time; }

    /** „Gewitter · 3,0 s je Blitz · 12 Blitze (4 Erdblitze, 6 in der Wolke, 1 Krone, 1 Wetterleuchten) · zuletzt … · Donner in 4,1 s“ */
    public String zeile() {
        StormLevel l = level;
        Lightning.Thunder next = null;
        for (Lightning.Thunder th : blitz.pendingThunder()) if (next == null || th.time() < next.time()) next = th;
        String th = next != null ? String.format(Locale.GERMAN, " · Donner in %.1f s", next.time() - blitz.time()) : "";
        return String.format(Locale.GERMAN, "Gewitter %s%s · %d Blitze (%d Erdblitze, %d in der Wolke, %d Kronen, %d Wetterleuchten), %d je Minute%s%s · Glutkern %.0f %%",
                l == null ? "–" : l.name(), regieAn ? "" : " (Regie aus)", erdblitze + wolkenblitze + kronen + wetterleuchten, erdblitze, wolkenblitze, kronen, wetterleuchten,
                regie.perMinute(time * 1000), zuletzt.isEmpty() ? "" : " · zuletzt " + zuletzt, th,
                100 * glutJetzt) + (sanft ? " · sanft" : "");
    }

    public String regenZeile() {
        return regen.rate <= 0 ? "Regen aus" : "Regen " + regen.describe();
    }
}
