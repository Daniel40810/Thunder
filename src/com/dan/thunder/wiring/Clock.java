package com.dan.thunder.wiring;

/**
 * Zeit der Szene. Gibt: die App ({@code SceneClock}). Nimmt: alle Packages, vor allem {@code sky} (Sonne über der See),
 * {@code tide} (Pegel Westerland, ab Phase 7a), {@code season} (Strandkörbe, Strandhafer) und {@code surf} (Brandungsschwebung). Die Laufzeit {@code t} läuft
 * auch bei stehender Uhr weiter (Wellen, Strähnen), die Uhrzeit {@code epochMillis} folgt dem Zeitraffer.
 */
public interface Clock {
    /** Laufzeit der Szene in s und letzter Schritt in s. */
    double t();
    double dt();

    /** Szenenzeit in ms seit 1970 (UTC). */
    double epochMillis();

    /** Ort in Grad (das Quermarkenfeuer Rotes Kliff). */
    double latDeg();
    double lonDeg();
}
