package com.dan.thunder.storm;

import java.util.List;

/**
 * Ein einzelner Blitz in Bildkoordinaten.
 *
 * @param nearness 0 = am Horizont, 1 = direkt vor dem Strand
 * @param gx       Einschlagstelle x (bei IC: Mitte der Entladung)
 * @param cx       Wurzel in der Wolke x
 * @param scale    Linienstärke (nahe Blitze sind dicker)
 * @param peakKa   Scheitelstrom in kA, null bei IC
 */
public record Strike(StrikeKind kind, boolean clicked, long seed, double bornMs, double distKm, double nearness,
                     double gx, double gy, double cx, double cy, double scale, Integer peakKa,
                     Envelope envelope, List<List<BoltSegment>> bolts) {

    public double thunderDelayMs() {
        return distKm * 2.92 * 1000.0;
    }

    public double thunderAtMs() {
        return bornMs + thunderDelayMs();
    }

    public double intensity(double nowMs) {
        return envelope.intensity(nowMs - bornMs) * (kind == StrikeKind.IC ? 0.8 : 1.0);
    }

    public boolean finished(double nowMs) {
        return nowMs - bornMs > envelope.durationMs();
    }
}
