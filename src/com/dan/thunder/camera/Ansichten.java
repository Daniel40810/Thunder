package com.dan.thunder.camera;

import com.dan.thunder.scene.Gelaende;
import com.dan.thunder.scene.Site;

import java.util.List;

/**
 * Die Ansichten auf den Tasten 1 bis 5 (F46 A): der Hauptblick wie in den fünf Bildern des 2D-Thunder, dazu Seebrücke,
 * Leuchtturm, Strandkorb und Drohne [G].
 */
public final class Ansichten {
    private Ansichten() { }

    /** Eine Ansicht: Taste, Name, Auge (x, z), Augenhöhe über Grund oder Deck, Blickrichtung, Neigung (+ nach unten). */
    public record Ansicht(int key, String name, double x, double z, double ueberGrund, double heading, double pitch) {
        /** Die Kamera dorthin stellen. */
        public void stellen(Camera c) {
            if (key == 1) { c.home(); return; }
            c.fovy = Camera.FOVY;
            double g = Math.max(0, Gelaende.height(x, z));
            if (key == 2) g = Site.DECK;
            c.place(x, g + ueberGrund, z, heading, pitch);
        }
    }

    public static final List<Ansicht> ALLE;
    static {
        double[] w = Site.BRUECKE_WURZEL, k = Site.BRUECKE_KOPF;
        double[] mitte = Site.welt(w[0] + (k[0] - w[0]) * 0.55, w[1] + (k[1] - w[1]) * 0.55);
        double[] wz = Site.welt(w[0], w[1]), kz = Site.welt(k[0], k[1]);
        double brueckeKurs = Math.toDegrees(Math.atan2(kz[0] - wz[0], -(kz[1] - wz[1])));
        double[] zunge = Site.welt(205, -238);
        double zumTurm = Math.toDegrees(Math.atan2(Site.LEUCHTTURM[0] - zunge[0], -(Site.LEUCHTTURM[1] - zunge[1])));
        double[] korb = Site.welt(Site.KOERBE[3][0] + 1.2, Site.KOERBE[3][1] + 1.6);
        double[] drohne = Site.welt(-110, -40);
        ALLE = List.of(
                new Ansicht(1, "Hauptblick", 0, 0, Site.AUGE, Site.BLICK, Camera.HAUPT_NEIGUNG),
                new Ansicht(2, "Seebrücke", mitte[0], mitte[1], 1.7, brueckeKurs - 8, -1.5),
                new Ansicht(3, "Leuchtturm", zunge[0], zunge[1], 1.7, zumTurm, -6),
                new Ansicht(4, "Strandkorb", korb[0], korb[1], 1.1, Site.BLICK - 10, -2),
                new Ansicht(5, "Drohne", drohne[0], drohne[1], 55, Site.BLICK + 5, 9));
    }

    public static Ansicht taste(int key) {
        for (Ansicht a : ALLE) if (a.key() == key) return a;
        return null;
    }
}
