package com.dan.thunder.gl;

import com.dan.cloud.Clouds;
import com.dan.ocean.Ocean;

import java.util.Locale;

/**
 * Qualitätsstufen (Phase 18a, aus Sylt entschlackt): Anteil der Bildgröße, See ({@code ocean}), Wolken
 * ({@code cloud}) und Reihen der Brandung ({@code surf}). Ziel wie Sylt (F28): „Mittel“ hält 60 Bilder/s in Full HD auf
 * der Radeon 680M, nie unter 30; „Hoch“ und „Ultra“ sind für Standbilder.
 */
public enum Quality {
    NIEDRIG("Niedrig", 0.67, Ocean.Quality.LOW, Clouds.Quality.FLAT, 24),
    MITTEL("Mittel", 0.85, Ocean.Quality.MEDIUM, Clouds.Quality.MEDIUM, 32),
    HOCH("Hoch", 1.0, Ocean.Quality.HIGH, Clouds.Quality.HIGH, 48),
    ULTRA("Ultra", 1.0, Ocean.Quality.ULTRA, Clouds.Quality.ULTRA, 64);

    public final String label;
    public final double renderScale;
    public final Ocean.Quality ocean;
    public final Clouds.Quality clouds;
    /** Reihen der Brandung längs des Strands. */
    public final int surfRows;

    Quality(String label, double renderScale, Ocean.Quality ocean, Clouds.Quality clouds, int surfRows) {
        this.label = label;
        this.renderScale = renderScale;
        this.ocean = ocean;
        this.clouds = clouds;
        this.surfRows = surfRows;
    }

    /** Bildzeit, die die Stufe höchstens verbrauchen darf (ms). */
    public double budgetMs() {
        return switch (this) { case NIEDRIG -> 8.0; case MITTEL -> 16.5; case HOCH -> 33.0; case ULTRA -> 50.0; };
    }

    public String summary() {
        return String.format(Locale.GERMAN, "%s: %d %% der Auflösung, Budget %.1f ms, See %d² × %d, Wolken %s, Brandung %d Reihen", label,
                Math.round(renderScale * 100), budgetMs(), ocean.n, ocean.cascades, clouds, surfRows);
    }

    @Override public String toString() { return label; }
}
