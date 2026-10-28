package com.dan.thunder.wiring;

import com.dan.cloud.CloudType;
import com.dan.cloud.Clouds;
import com.dan.ocean.Ocean;
import com.dan.sky.Sky;
import com.dan.thunder.model.Mood;
import com.dan.thunder.model.StormLevel;

import java.util.Locale;

/**
 * Die Verdrahtung (Phase 18a): die Gewitterstufe aus {@code TH_STORM_LEVEL} und die Stimmung (Abend, Nacht) als
 * Eingänge für {@code sky}, {@code cloud}, {@code ocean} und {@code surf} [G]:
 * <ul>
 * <li><b>Wind</b>: 3 + 8·WIND m/s aus Südwest (das Gewitter zieht über Land heran; Wetterleuchten 5,8, Gewitter 8,2,
 * Unwetter 11 m/s). Ablandig: die Windsee draußen bleibt kurz (Fetch 2 km).</li>
 * <li><b>See am Strand</b>: auflandige Restsee aus Nordost wie {@code surf.ostsee()}: H_s 0,35 + 0,55·WIND m, T_p 3,6 +
 * 1,2·WIND s.</li>
 * <li><b>Wolken</b>: Wetterleuchten 5/8 Cumulonimbus in der Ferne, Gewitter 6/8 (seit 18b: zwischen den Zellen scheint die Dämmerung durch wie in den Bildern), Unwetter 8/8; Sicht 30, 16, 8 km.</li>
 * </ul>
 * Ohne Stufe (vor dem Laden) gilt „Gewitter“.
 */
public final class Wiring {
    public final Clock clock;
    public volatile StormLevel level;
    public volatile Mood mood = Mood.ABEND;
    /** Ob die Wolken gezeichnet werden (sonst übernimmt der Himmel die Bedeckung als Schleier). */
    public volatile boolean cloudsDrawn = true;

    public Wiring(Clock clock) { this.clock = clock; }

    /** WIND der Stufe 0..2 (ohne Stufe 0,65 wie „Gewitter“). */
    public double wind() { StormLevel l = level; return l == null ? 0.65 : l.wind(); }

    public double windSpeed() { return 3 + 8 * wind(); }
    public double windFromDeg() { return 225; }
    public double seaHs() { return 0.35 + 0.55 * wind(); }
    public double seaTp() { return 3.6 + 1.2 * wind(); }
    public double seaFromDeg() { return 45; }

    public double oktas() { double w = wind(); return w < 0.5 ? 5 : w < 0.9 ? 6 : 8; }
    public double visibilityKm() { double w = wind(); return w < 0.5 ? 30 : w < 0.9 ? 16 : 8; }
    public CloudType cloudType() { return CloudType.CUMULONIMBUS; }

    public void feed(Sky sky, double eyeAsl) {
        sky.epochMillis = clock.epochMillis();
        sky.latDeg = clock.latDeg();
        sky.lonDeg = clock.lonDeg();
        sky.eyeHeight = Math.max(1, eyeAsl);
        sky.visibilityKm = visibilityKm();
        sky.groundAlbedo = 0.10;
        sky.cloudCover = cloudsDrawn ? 0 : oktas() / 8;
        sky.cloudVeil = !cloudsDrawn;
    }

    public void feed(Clouds c) {
        c.oktas = oktas();
        c.type = cloudType();
        c.highCover = 0.6;
        c.windSpeed = Math.max(3, windSpeed() * 1.6);
        c.windFromDeg = (windFromDeg() + 20) % 360;
        c.lens = 0;
    }

    public void feed(Ocean o) {
        // die See baut ihr Spektrum bei jeder Änderung neu: nur in Stufen weiter (wie Lighthouse)
        double ws = Math.round(windSpeed() * 2) / 2.0;
        if (Math.abs(ws - o.windSpeed) >= 0.5) o.windSpeed = ws;
        o.windFromDeg = windFromDeg();
        o.fetchLimited = true;
        o.fetch = 2000;
        double sh = Math.round(seaHs() * 20) / 20.0, sp = Math.round(seaTp() * 4) / 4.0;
        if (Math.abs(sh - o.swellHeight) >= 0.05) o.swellHeight = sh;
        if (Math.abs(sp - o.swellPeriod) >= 0.25) o.swellPeriod = sp;
        o.swellFromDeg = seaFromDeg();
        o.waterLevel = 0;
    }

    public String zeile() {
        StormLevel l = level;
        return String.format(Locale.GERMAN, "%s · %s · Wind %.1f m/s aus %03.0f° · See am Strand H_s %.2f m, T_p %.1f s aus %03.0f° · %.0f/8 %s · Sicht %.0f km",
                l == null ? "Gewitter (Vorgabe)" : l.name(), mood.getLabel(), windSpeed(), windFromDeg(), seaHs(), seaTp(), seaFromDeg(), oktas(), cloudType().label, visibilityKm());
    }
}
