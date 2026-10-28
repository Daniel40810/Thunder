package com.dan.thunder.camera;

import com.dan.thunder.model.ViewDef;
import com.dan.thunder.scene.Gelaende;
import com.dan.thunder.scene.Site;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Ansichten auf den Tasten 1 bis 5 (F46 A): der Hauptblick wie in den fünf Bildern des 2D-Thunder, dazu Seebrücke,
 * Leuchtturm, Strandkorb und Drohne [G]. Seit Phase 18c kommen sie aus {@code TH_VIEW}; ohne Datenbank (oder ohne
 * {@code sql/04_th_3d.sql}) gelten die eingebauten {@link #VORGABE}.
 */
public final class Ansichten {
    private Ansichten() { }

    /**
     * Eine Ansicht: Taste, Name, Auge (x, z), Augenhöhe über Grund oder Deck, Blickrichtung, Neigung (positiv nach
     * unten, negativ nach oben), auf dem Deck der Seebrücke oder nicht, Öffnungswinkel.
     */
    public record Ansicht(int key, String name, double x, double z, double ueberGrund, double heading, double pitch, boolean deck, double fovy) {
        public Ansicht(int key, String name, double x, double z, double ueberGrund, double heading, double pitch) {
            this(key, name, x, z, ueberGrund, heading, pitch, key == 2, Camera.FOVY);
        }

        /** Die Kamera dorthin stellen; der Hauptblick in der Vorgabe ist {@link Camera#home()}. */
        public void stellen(Camera c) {
            if (key == 1 && Math.hypot(x, z) < 0.05 && Math.abs(ueberGrund - Site.AUGE) < 0.01 && Math.abs(heading - Site.BLICK) < 0.05
                    && Math.abs(pitch - Camera.HAUPT_NEIGUNG) < 0.05 && Math.abs(fovy - Camera.FOVY) < 0.05) { c.home(); return; }
            c.fovy = fovy;
            double g = deck ? Site.DECK : Math.max(0, Gelaende.height(x, z));
            c.place(x, g + ueberGrund, z, heading, pitch);
        }

        public ViewDef alsDaten() { return new ViewDef(key, name, r2(x), r2(z), r2(ueberGrund), deck ? "DECK" : "GRUND", r1(heading), r1(pitch), r1(fovy)); }

        public static Ansicht aus(ViewDef v) {
            return new Ansicht(v.key(), v.name(), v.xM(), v.zM(), v.aboveM(), v.headingDeg(), v.pitchDeg(), v.onDeck(), v.fovyDeg());
        }
    }

    static double r2(double v) { return Math.round(v * 100) / 100.0; }

    static double r1(double v) { return Math.round(v * 10) / 10.0; }

    /** Die eingebauten Ansichten (wie in {@code sql/04_th_3d.sql}). */
    public static final List<Ansicht> VORGABE;
    /** Die geltenden Ansichten: aus TH_VIEW oder die Vorgabe. */
    public static volatile List<Ansicht> ALLE;
    static {
        double[] w = Site.BRUECKE_WURZEL, k = Site.BRUECKE_KOPF;
        double[] mitte = Site.welt(w[0] + (k[0] - w[0]) * 0.55, w[1] + (k[1] - w[1]) * 0.55);
        double[] wz = Site.welt(w[0], w[1]), kz = Site.welt(k[0], k[1]);
        double brueckeKurs = Math.toDegrees(Math.atan2(kz[0] - wz[0], -(kz[1] - wz[1])));
        double[] zunge = Site.welt(205, -238);
        double zumTurm = Math.toDegrees(Math.atan2(Site.LEUCHTTURM[0] - zunge[0], -(Site.LEUCHTTURM[1] - zunge[1])));
        double[] korb = Site.welt(Site.KOERBE[3][0] + 1.2, Site.KOERBE[3][1] + 1.6);
        double[] drohne = Site.welt(-110, -40);
        List<Ansicht> roh = List.of(
                new Ansicht(1, "Hauptblick", 0, 0, Site.AUGE, Site.BLICK, Camera.HAUPT_NEIGUNG),
                new Ansicht(2, "Seebrücke", mitte[0], mitte[1], 1.7, brueckeKurs - 8, -1.5),
                new Ansicht(3, "Leuchtturm", zunge[0], zunge[1], 1.7, zumTurm, -6),
                new Ansicht(4, "Strandkorb", korb[0], korb[1], 1.1, Site.BLICK - 10, -2),
                new Ansicht(5, "Drohne", drohne[0], drohne[1], 55, Site.BLICK + 5, 9));
        // gerundet wie in der Datenbank (cm, Zehntelgrad), damit Vorgabe und TH_VIEW dasselbe Bild geben
        List<Ansicht> v = new ArrayList<>();
        for (Ansicht a : roh) v.add(Ansicht.aus(a.alsDaten()));
        VORGABE = List.copyOf(v);
        ALLE = VORGABE;
    }

    /** Ansichten aus den Daten setzen (leer oder null: die Vorgabe). */
    public static void setzen(List<ViewDef> daten) {
        if (daten == null || daten.isEmpty()) { ALLE = VORGABE; return; }
        List<Ansicht> v = new ArrayList<>();
        for (ViewDef d : daten) v.add(Ansicht.aus(d));
        ALLE = List.copyOf(v);
    }

    public static Ansicht taste(int key) {
        for (Ansicht a : ALLE) if (a.key() == key) return a;
        return null;
    }
}
