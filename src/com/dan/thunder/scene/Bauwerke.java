package com.dan.thunder.scene;

import java.util.Locale;

/**
 * Leuchtturm, Seebrücke und Strandkörbe als ein Netz für {@code bauten.vert/frag} (Phase 18a), frei gestaltet wie im
 * 2D-Thunder [G]:
 * <ul>
 * <li><b>Leuchtturm</b> auf der Landzunge: Rundturm 22 m mit roten Bändern (weiß, rot, weiß, rot, weiß), Galerie mit
 * Geländer, Laterne (Glas wie Kampen, sie leuchtet in Phase 18b), Kuppel; ein Wärterhaus am Fuß.</li>
 * <li><b>Seebrücke</b>: rund 230 m vom Strand nach Ostnordost, Deck 3,2 m über Mittelwasser, Pfähle alle 6 m, Geländer,
 * am Kopf ein kleines Haus mit einer Laterne.</li>
 * <li><b>Strandkörbe</b>: vier wie in {@code TH_SCENE_OBJECT}, mit Streifen in Blau, Grün, Blau und Rot, die Öffnung zum
 * Blick.</li>
 * </ul>
 */
public final class Bauwerke {
    public final Netz netz = new Netz();
    /** Laterne des Leuchtturms (x, Brennpunkt y, z) und der Seebrücke. */
    public final double[] laterneTurm, laterneBruecke;
    /** Pfähle der Seebrücke im Wasser {x, z} (für den Klang: die Wellen glucksen daran). */
    public final java.util.List<double[]> pfaehle = new java.util.ArrayList<>();
    /** Farben der Körbe als Kennzahlen für die Streifen in bauten.frag (Blau, Grün, Blau, Rot). */
    public static final String[] KORB_FARBEN = {"#3C64BE", "#2E8B57", "#3C64BE", "#C8102E"};

    public Bauwerke() {
        double lx = Site.LEUCHTTURM[0], lz = Site.LEUCHTTURM[1];
        double g = Gelaende.height(lx, lz);
        laterneTurm = turm(lx, g, lz);
        laterneBruecke = bruecke();
        for (int i = 0; i < Site.KOERBE.length; i++) {
            double[] k = Site.KOERBE[i], p = Site.welt(k[0], k[1]);
            double y = Gelaende.height(p[0], p[1]);
            // Öffnung zum Hauptblick (Peilung vom Korb zum Ursprung), leicht gestreut
            double zumBlick = Math.toDegrees(Math.atan2(-p[0], p[1])) + (i % 2 == 0 ? 8 : -6);
            korb(netz, p[0], y, p[1], zumBlick, kennzahl(KORB_FARBEN[i]));
        }
    }

    /** Leuchtturm; gibt den Brennpunkt der Laterne zurück. */
    double[] turm(double x, double g, double z) {
        int weiss = Netz.merkmal(Netz.LACK, false, 3, g), rot = Netz.merkmal(Netz.ROT, false, 3, g);
        int schwarz = Netz.merkmal(Netz.LACK_SCHWARZ, false, 3, g), glas = Netz.merkmal(Netz.GLAS_K, false, 3, g);
        int kupfer = Netz.merkmal(Netz.KUPFER, false, 3, g), granit = Netz.merkmal(Netz.GRANIT, false, 3, g);
        double h = Site.TURM_HOEHE, r0 = 2.7, r1 = 1.9;
        netz.prisma(x, z, g - 0.6, g + 0.6, 3.4, 3.2, 24, 0, true, true, false, granit);
        double[] bands = {0, 4.6, 8.4, 12.2, 16.0, h};
        for (int i = 0; i < bands.length - 1; i++) {
            double ya = g + 0.6 + bands[i] * (h - 0.6) / h, yb = g + 0.6 + bands[i + 1] * (h - 0.6) / h;
            double ra = r0 + (r1 - r0) * (ya - g) / h, rb = r0 + (r1 - r0) * (yb - g) / h;
            netz.prisma(x, z, ya, yb, ra, rb, 32, 0, true, false, false, i % 2 == 0 ? weiss : rot);
        }
        double top = g + h;
        netz.scheibe(x, z, top + 0.25, 0, r1 + 0.9, 0.25, 24, 0, schwarz);
        netz.gelaender(x, z, top + 0.25, r1 + 0.8, 1.0, 16, 0, 0.05, schwarz);
        netz.prisma(x, z, top + 0.25, top + 0.6, 1.35, 1.35, 16, 0, false, false, false, schwarz);
        netz.prisma(x, z, top + 0.6, top + 2.6, 1.3, 1.3, 16, 0, true, false, false, glas);
        netz.prisma(x, z, top + 2.6, top + 2.75, 1.5, 1.5, 16, 0, false, true, false, schwarz);
        netz.prisma(x, z, top + 2.75, top + 3.7, 1.45, 0.15, 16, 0, true, false, false, kupfer);
        netz.kugel(x, top + 3.85, z, 0.18, schwarz);
        // Wärterhaus am Fuß (zur Landseite)
        double[] off = Site.peilung(205, 7.5);
        int putz = Netz.merkmal(Netz.PUTZ, true, 11, g), dach = Netz.merkmal(Netz.DACH_ROT, false, 11, g);
        netz.quader(x + off[0], g - 0.2, z + off[1], 6, 4.5, 3.0, 25, putz);
        satteldach(x + off[0], g + 2.8, z + off[1], 6.4, 4.9, 2.0, 25, dach);
        return new double[]{x, top + 1.6, z};
    }

    /** Satteldach über einem Quader (First längs „quer“). */
    void satteldach(double x, double y, double z, double sx, double sz, double h, double richtung, int m) {
        double a = Math.toRadians(richtung), fx = Math.sin(a), fz = -Math.cos(a), rx = -fz, rz = fx;
        double[] l0 = {x - rx * sx / 2 - fx * sz / 2, y, z - rz * sx / 2 - fz * sz / 2}, l1 = {x + rx * sx / 2 - fx * sz / 2, y, z + rz * sx / 2 - fz * sz / 2};
        double[] v0 = {x - rx * sx / 2 + fx * sz / 2, y, z - rz * sx / 2 + fz * sz / 2}, v1 = {x + rx * sx / 2 + fx * sz / 2, y, z + rz * sx / 2 + fz * sz / 2};
        double[] f0 = {x - rx * sx / 2, y + h, z - rz * sx / 2}, f1 = {x + rx * sx / 2, y + h, z + rz * sx / 2};
        netz.viereck(l0, l1, f1, f0, m);
        netz.viereck(v1, v0, f0, f1, m);
        netz.dreieck(l0, f0, v0, m);
        netz.dreieck(l1, v1, f1, m);
    }

    /** Seebrücke; gibt den Brennpunkt der Laterne am Kopf zurück. */
    double[] bruecke() {
        double[] a = Site.welt(Site.BRUECKE_WURZEL[0], Site.BRUECKE_WURZEL[1]), b = Site.welt(Site.BRUECKE_KOPF[0], Site.BRUECKE_KOPF[1]);
        double dx = b[0] - a[0], dz = b[1] - a[1], len = Math.hypot(dx, dz);
        double ux = dx / len, uz = dz / len, qx = -uz, qz = ux;
        double richtung = Math.toDegrees(Math.atan2(ux, -uz));
        double deck = Site.DECK, w = Site.BRUECKE_BREITE;
        int holz = Netz.merkmal(Netz.HOLZ, false, 21, 0), pfahl = Netz.merkmal(Netz.HOLZ, false, 22, -6), eisen = Netz.merkmal(Netz.EISEN, false, 23, 0);
        // Deck in Stücken (folgt am Strand dem Gelände, draußen eben)
        int stuecke = (int) Math.ceil(len / 6);
        for (int i = 0; i < stuecke; i++) {
            double t0 = i * len / stuecke, t1 = (i + 1) * len / stuecke;
            double x0 = a[0] + ux * t0, z0 = a[1] + uz * t0, x1 = a[0] + ux * t1, z1 = a[1] + uz * t1;
            double y0 = deckHoehe(x0, z0, deck), y1 = deckHoehe(x1, z1, deck);
            netz.balken(new double[]{x0, y0 - 0.15, z0}, new double[]{x1, y1 - 0.15, z1}, w, 0.3, holz);
            // Pfähle (zwei je Joch) bis in den Grund
            for (double s : new double[]{-0.5, 0.5}) {
                double px = x0 + qx * s * (w - 0.3), pz = z0 + qz * s * (w - 0.3);
                double grund = Gelaende.height(px, pz);
                if (y0 - 0.3 - grund > 0.4) netz.balken(new double[]{px, grund - 0.3, pz}, new double[]{px, y0 - 0.3, pz}, 0.32, 0.32, pfahl);
                if (grund < -0.2 && s < 0) pfaehle.add(new double[]{px, pz});
            }
            // Geländer: Pfosten und Handlauf beidseits
            for (double s : new double[]{-0.5, 0.5}) {
                double ox = qx * s * (w - 0.08), oz = qz * s * (w - 0.08);
                netz.balken(new double[]{x0 + ox, y0, z0 + oz}, new double[]{x0 + ox, y0 + 1.05, z0 + oz}, 0.07, 0.07, eisen);
                netz.balken(new double[]{x0 + ox, y0 + 1.05, z0 + oz}, new double[]{x1 + ox, y1 + 1.05, z1 + oz}, 0.06, 0.05, eisen);
                netz.balken(new double[]{x0 + ox, y0 + 0.55, z0 + oz}, new double[]{x1 + ox, y1 + 0.55, z1 + oz}, 0.04, 0.04, eisen);
            }
        }
        // Kopf: Plattform, Haus, Laternenmast
        double kx = b[0] + ux * 4, kz = b[1] + uz * 4;
        int putz = Netz.merkmal(Netz.HOLZ_WEISS, true, 24, deck), dach = Netz.merkmal(Netz.DACH_GRAU, false, 24, deck);
        netz.quader(kx, deck - 0.3, kz, 9, 9, 0.3, richtung, holz);
        for (double s : new double[]{-4, 0, 4})
            for (double f : new double[]{-4, 0, 4}) {
                double px = kx + qx * s + ux * f, pz = kz + qz * s + uz * f;
                netz.balken(new double[]{px, Gelaende.height(px, pz) - 0.3, pz}, new double[]{px, deck - 0.3, pz}, 0.36, 0.36, pfahl);
            }
        netz.quader(kx + ux * 1.5, deck, kz + uz * 1.5, 3.2, 2.6, 2.4, richtung, putz);
        satteldach(kx + ux * 1.5, deck + 2.4, kz + uz * 1.5, 3.6, 3.0, 1.3, richtung, dach);
        double mx = kx - ux * 3.2 + qx * 3.2, mz = kz - uz * 3.2 + qz * 3.2;
        netz.balken(new double[]{mx, deck, mz}, new double[]{mx, deck + 3.6, mz}, 0.12, 0.12, eisen);
        int glas = Netz.merkmal(Netz.GLAS_Q, false, 25, deck);
        netz.prisma(mx, mz, deck + 3.6, deck + 4.1, 0.22, 0.22, 8, 0, true, false, false, glas);
        netz.prisma(mx, mz, deck + 4.1, deck + 4.35, 0.3, 0.02, 8, 0, true, false, false, eisen);
        return new double[]{mx, deck + 3.85, mz};
    }

    /** Deck der Brücke: draußen fest, am Strand mindestens 0,8 m über dem Sand. */
    static double deckHoehe(double x, double z, double deck) { return Math.max(deck, Gelaende.height(x, z) + 0.8); }

    /** Ein Strandkorb (aus Sylt: 1,25 × 0,85 × 1,6 m), Öffnung nach {@code richtung}. */
    public static void korb(Netz n, double x, double y, double z, double richtung, int kennzahl) {
        int korb = Netz.merkmal(Netz.KORB, false, kennzahl, y), segel = Netz.merkmal(Netz.SEGEL, false, kennzahl, y);
        double a = Math.toRadians(richtung), fx = Math.sin(a), fz = -Math.cos(a);
        n.quader(x, y - 0.05, z, 1.25, 0.85, 0.50, richtung, korb);
        n.quader(x - fx * 0.32, y + 0.45, z - fz * 0.32, 1.25, 0.18, 1.10, richtung, korb);
        for (double s : new double[]{-0.58, 0.58}) {
            double rx = -fz * s, rz = fx * s;
            n.quader(x + rx - fx * 0.05, y + 0.45, z + rz - fz * 0.05, 0.09, 0.62, 0.75, richtung, korb);
        }
        double[] h0 = {x - fx * 0.42 + fz * 0.66, y + 1.62, z - fz * 0.42 - fx * 0.66}, h1 = {x - fx * 0.42 - fz * 0.66, y + 1.62, z - fz * 0.42 + fx * 0.66};
        double[] h2 = {x + fx * 0.20 - fz * 0.66, y + 1.25, z + fz * 0.20 + fx * 0.66}, h3 = {x + fx * 0.20 + fz * 0.66, y + 1.25, z + fz * 0.20 - fx * 0.66};
        n.viereck(h0, h1, h2, h3, korb);
        double cx = x - fx * 0.225, cz = z - fz * 0.225;
        double[] s0 = {cx + fz * 0.56, y + 0.5, cz - fx * 0.56}, s1 = {cx - fz * 0.56, y + 0.5, cz + fx * 0.56};
        double[] s2 = {cx - fz * 0.56, y + 1.5, cz + fx * 0.56}, s3 = {cx + fz * 0.56, y + 1.5, cz - fx * 0.56};
        n.dreieck(s0, s1, s2, new double[]{fx, 0, fz}, segel);
        n.dreieck(s0, s2, s3, new double[]{fx, 0, fz}, segel);
    }

    /**
     * Kennzahl, deren Streifenfarbe in bauten.frag der gewünschten am nächsten kommt (dort: hash(Kennzahl · 5,3) wählt
     * Blau, Rot, Grün, Gelb oder Dunkelblau).
     */
    public static int kennzahl(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        double r = (rgb >> 16 & 255) / 255.0, g = (rgb >> 8 & 255) / 255.0, b = (rgb & 255) / 255.0;
        int ziel = b > r && b > g ? 0 : r > g ? 1 : 2;   // 0 blau, 1 rot, 2 grün
        for (int k = 1; k < 511; k++) {
            double s = fract(Math.sin(k * 5.3f) * 43758.5453);
            int f = s < 0.3 ? 0 : s < 0.5 ? 1 : s < 0.65 ? 2 : -1;
            if (f == ziel) return k;
        }
        return 1;
    }

    static double fract(double v) { return v - Math.floor(v); }

    public String describe() {
        return String.format(Locale.GERMAN, "Leuchtturm, Seebrücke und %d Strandkörbe: %,d Dreiecke", Site.KOERBE.length, netz.dreiecke());
    }
}
