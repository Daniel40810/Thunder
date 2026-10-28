package com.dan.thunder.scene;

/**
 * Der Ort der Szene (Phase 18a, F47 A): ein frei gestalteter Ostseestrand an der Lübecker Bucht, nach Nordost offen.
 * Gedachter Ort für Sonne, Mond und Sterne: 54,0° N, 10,8° O, Mitte Juli. Keine Tide.
 * <p>
 * Raum in m: x Ost, y oben (0 = Mittelwasser), z Süd. Ursprung ist der Standpunkt des Hauptblicks auf der Düne. Viele
 * Lagen stehen hier im Blickrahmen: s voraus (Richtung {@link #BLICK} = 45°, Nordost), r nach rechts (Südost). Die
 * Lagen sind aus den fünf Bildern in {@code docs/} des 2D-Thunder abgelesen (Horizont 0,60, Ufer 0,80 der Bildhöhe,
 * Leuchtturm bei 0,14, Seebrücke bis 0,71, Strandkörbe bei 0,37 bis 0,60) [G].
 */
public final class Site {
    private Site() { }

    public static final double LAT = 54.0, LON = 10.8;
    public static final String ZONE = "Europe/Berlin";
    /** Richtung des Hauptblicks (Grad von Nord) und seine Einheitsvektoren in der Szene. */
    public static final double BLICK = 45;
    public static final double FX = Math.sin(Math.toRadians(BLICK)), FZ = -Math.cos(Math.toRadians(BLICK));
    public static final double RX = -FZ, RZ = FX;

    /** Szenenpunkt {x, z} aus dem Blickrahmen (s voraus, r rechts). */
    public static double[] welt(double s, double r) { return new double[]{s * FX + r * RX, s * FZ + r * RZ}; }

    /** Blickrahmen {s, r} aus der Szene. */
    public static double[] rahmen(double x, double z) { return new double[]{x * FX + z * FZ, x * RX + z * RZ}; }

    /** Szenenpunkt aus Peilung (Grad von Nord) und Abstand vom Ursprung. */
    public static double[] peilung(double grad, double d) {
        double a = Math.toRadians(grad);
        return new double[]{d * Math.sin(a), -d * Math.cos(a)};
    }

    /** Augenhöhe des Hauptblicks über dem Grund (m) und Grund am Standpunkt (m). */
    public static final double AUGE = 1.6;

    /** Leuchtturm auf der Landzunge: Peilung 12°, 400 m [G]; Höhe des Turms bis zur Galerie, Feuerhöhe. */
    public static final double[] LEUCHTTURM = peilung(12, 400);
    public static final double TURM_HOEHE = 22, FEUER_HOEHE = 23.5;

    /** Seebrücke: Wurzel am Strand und Kopf draußen (Blickrahmen s, r) [G], Deck über Mittelwasser, Breite. */
    public static final double[] BRUECKE_WURZEL = {9, 13}, BRUECKE_KOPF = {255, 108};
    public static final double DECK = 3.2, BRUECKE_BREITE = 3.0;

    /** Strandkörbe (Blickrahmen s, r) in der Reihenfolge von TH_SCENE_OBJECT (POS_X 0,43, 0,51, 0,37, 0,60) [G]. */
    public static final double[][] KOERBE = {{19.5, -2.6}, {17.5, 0.3}, {15.0, -3.6}, {12.5, 2.3}};
}
