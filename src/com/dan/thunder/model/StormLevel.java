package com.dan.thunder.model;

/**
 * Gewitterstufe (TH_STORM_LEVEL).
 *
 * @param rateSec    mittlerer Abstand zwischen zwei Blitzen
 * @param shareIc    Anteil Blitze in der Wolke
 * @param shareCrown Anteil Blitzkronen; der Rest sind Wolke-Erde-Blitze
 */
public record StormLevel(int id, String code, String name, double rateSec, double shareIc, double shareCrown,
                         double distMinKm, double distMaxKm, int rainDrops, double wind, double ember,
                         String description) {

    @Override
    public String toString() {
        return name;
    }
}
