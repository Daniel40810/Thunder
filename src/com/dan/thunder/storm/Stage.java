package com.dan.thunder.storm;

/** Bildgeometrie, die der Gewitter-Regisseur zum Platzieren der Blitze braucht. */
public interface Stage {
    double width();

    double height();

    double horizonY();

    /** Uferlinie an Stelle x (leicht geschwungen). */
    double shoreY(double x);

    double emberX();

    double emberY();

    /** Maßstab für Längen: max(Breite, 1,5 · Höhe). */
    default double span() {
        return Math.max(width(), height() * 1.5);
    }
}
