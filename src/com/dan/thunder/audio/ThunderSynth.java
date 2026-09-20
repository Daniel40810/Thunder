package com.dan.thunder.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Erzeugt den Ton komplett selbst, ohne Audiodateien: Regen (gefiltertes weißes Rauschen),
 * Brandung (tiefes Rauschen mit langsamer Wellenbewegung) und Donner (braunes Rauschen durch
 * einen Tiefpass, der mit der Entfernung dunkler wird; nahe Einschläge beginnen mit einem Knall).
 * <p>
 * Läuft in einem eigenen Thread, der fortlaufend in eine {@link SourceDataLine} schreibt.
 */
public final class ThunderSynth {

    private static final float RATE = 44_100f;
    private static final int BLOCK = 1024;

    private final ConcurrentLinkedQueue<Voice> incoming = new ConcurrentLinkedQueue<>();
    private final Random rnd = new Random();
    private volatile boolean running;
    private volatile double rainTarget;
    private volatile double surfTarget;
    private volatile String error;
    private Thread thread;

    /** Startet die Ausgabe. Gibt false zurück, wenn kein Audiogerät verfügbar ist ({@link #error()}). */
    public synchronized boolean start() {
        if (running) {
            return true;
        }
        try {
            AudioFormat fmt = new AudioFormat(RATE, 16, 1, true, false);
            SourceDataLine line = AudioSystem.getSourceDataLine(fmt);
            line.open(fmt, BLOCK * 2 * 6);
            line.start();
            running = true;
            error = null;
            thread = new Thread(() -> loop(line), "thunder-synth");
            thread.setDaemon(true);
            thread.start();
            return true;
        } catch (Exception | LinkageError ex) {
            error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return false;
        }
    }

    public synchronized void stop() {
        running = false;
        thread = null;
    }

    public boolean isRunning() {
        return running;
    }

    public String error() {
        return error;
    }

    /** Lautstärke des Regens 0..1. */
    public void setRain(double level) {
        rainTarget = Math.max(0, Math.min(1, level));
    }

    /** Lautstärke der Brandung 0..1. */
    public void setSurf(double level) {
        surfTarget = Math.max(0, Math.min(1, level));
    }

    /** Donner eines Blitzes in {@code distKm} Entfernung, jetzt abspielen. */
    public void thunder(double distKm) {
        if (running) {
            incoming.add(new Thunder(distKm, rnd.nextLong()));
        }
    }

    /**
     * Rechnet {@code seconds} Sekunden Klang ohne Audiogerät (für Tests): Regen und Brandung auf den
     * gesetzten Pegeln, dazu ein Donner in {@code distKm} Entfernung zu Beginn. Werte −1..1.
     */
    public float[] renderOffline(double seconds, double distKm) {
        int n = (int) (seconds * RATE);
        float[] out = new float[n];
        Mixer mixer = new Mixer();
        mixer.voices.add(new Thunder(distKm, 42L));
        mixer.rainGain = rainTarget * 0.22;
        mixer.surfGain = surfTarget * 0.5;
        double[] mix = new double[BLOCK];
        for (int pos = 0; pos < n; pos += BLOCK) {
            mixer.block(mix);
            for (int i = 0; i < BLOCK && pos + i < n; i++) {
                out[pos + i] = (float) (Math.tanh(mix[i] * 1.1) * 0.9);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ Mischpult

    /** Zustand der Tonerzeugung; ein Block nach dem anderen. */
    private final class Mixer {
        final List<Voice> voices = new ArrayList<>();
        final Noise rainNoise = new Noise(1);
        final Biquad rainBp = Biquad.bandpass(2800, 0.55);
        final Biquad rainHp = Biquad.highpass(900, 0.7);
        final Noise surfNoise = new Noise(2);
        final Biquad surfLp = Biquad.lowpass(380, 0.7);
        double rainGain, surfGain, surfPhase;

        void block(double[] mix) {
            Voice v;
            while ((v = incoming.poll()) != null) {
                voices.add(v);
            }
            double rt = rainTarget * 0.22, st = surfTarget * 0.5;
            for (int i = 0; i < mix.length; i++) {
                rainGain += (rt - rainGain) * 0.0004;
                surfGain += (st - surfGain) * 0.0002;
                surfPhase += 2 * Math.PI * 0.09 / RATE;
                double swell = 0.55 + 0.45 * Math.pow(0.5 + 0.5 * Math.sin(surfPhase), 3);
                mix[i] = rainHp.process(rainBp.process(rainNoise.white())) * rainGain
                        + surfLp.process(surfNoise.brown()) * surfGain * swell;
            }
            for (int k = voices.size() - 1; k >= 0; k--) {
                if (!voices.get(k).render(mix)) {
                    voices.remove(k);
                }
            }
        }
    }

    private void loop(SourceDataLine line) {
        byte[] out = new byte[BLOCK * 2];
        double[] mix = new double[BLOCK];
        Mixer mixer = new Mixer();
        try {
            while (running) {
                mixer.block(mix);
                for (int i = 0; i < BLOCK; i++) {
                    double s = Math.tanh(mix[i] * 1.1) * 0.9; // weiche Begrenzung statt Übersteuern
                    int v16 = (int) (s * 32767);
                    out[2 * i] = (byte) v16;
                    out[2 * i + 1] = (byte) (v16 >> 8);
                }
                line.write(out, 0, out.length);
            }
        } finally {
            line.drain();
            line.stop();
            line.close();
        }
    }

    private interface Voice {
        /** Addiert in {@code mix}; false = fertig. */
        boolean render(double[] mix);
    }

    /** Donner: Hüllkurve mit zufälligen Grollwellen, Tiefpass nach Entfernung, Knall bei nahen Blitzen. */
    private static final class Thunder implements Voice {
        private final Noise noise;
        private final Noise crackNoise;
        private final Biquad lp;
        private final Biquad lp2;
        private final Biquad crackHp;
        private final double[] bumps;
        private final int length;
        private final int attack;
        private final double peak;
        private final double crack;
        private int pos;

        Thunder(double dist, long seed) {
            Random r = new Random(seed);
            noise = new Noise(seed);
            crackNoise = new Noise(seed ^ 0x5DEECE66DL);
            double cutoff = 70 + 900 / (1 + dist * 1.3);
            lp = Biquad.lowpass(cutoff, 0.8);
            lp2 = Biquad.lowpass(cutoff * 1.4, 0.7);
            crackHp = Biquad.highpass(700, 0.7);
            double seconds = 3.2 + dist * 0.55 + r.nextDouble() * 2.2;
            length = (int) (seconds * RATE);
            attack = (int) ((dist < 2 ? 0.012 : 0.08 + dist * 0.03) * RATE);
            peak = 1.25 / (0.7 + dist * 0.33);
            crack = dist < 2.2 ? (2.2 - dist) * 0.7 : 0;
            bumps = new double[48];
            for (int i = 0; i < bumps.length; i++) {
                bumps[i] = 0.35 + 0.65 * r.nextDouble();
            }
        }

        @Override
        public boolean render(double[] mix) {
            for (int i = 0; i < mix.length && pos < length; i++, pos++) {
                double x = (double) pos / length;
                double env = Math.pow(1 - x, 1.7) * (pos < attack ? (double) pos / attack : 1);
                double bp = x * (bumps.length - 1);
                int bi = (int) bp;
                double bf = bp - bi, bump = bumps[bi] + (bumps[Math.min(bumps.length - 1, bi + 1)] - bumps[bi]) * smooth(bf);
                double s = lp2.process(lp.process(noise.brown())) * env * bump * peak * 2.2;
                if (crack > 0 && pos < RATE * 0.4) {
                    double ce = Math.exp(-pos / (RATE * 0.06));
                    s += crackHp.process(crackNoise.white()) * ce * crack;
                }
                mix[i] += s;
            }
            return pos < length;
        }

        private static double smooth(double t) {
            return t * t * (3 - 2 * t);
        }
    }

    /** Rauschquelle: weiß oder braun (integriert). */
    private static final class Noise {
        private final Random r;
        private double brown;

        Noise(long seed) {
            r = new Random(seed);
        }

        double white() {
            return r.nextDouble() * 2 - 1;
        }

        double brown() {
            brown = (brown + 0.02 * white()) / 1.02;
            return brown * 3.5;
        }
    }

    /** Biquad-Filter nach dem Audio-EQ-Kochbuch (R. Bristow-Johnson). */
    private static final class Biquad {
        private final double b0, b1, b2, a1, a2;
        private double x1, x2, y1, y2;

        private Biquad(double b0, double b1, double b2, double a0, double a1, double a2) {
            this.b0 = b0 / a0;
            this.b1 = b1 / a0;
            this.b2 = b2 / a0;
            this.a1 = a1 / a0;
            this.a2 = a2 / a0;
        }

        static Biquad lowpass(double f, double q) {
            double w = 2 * Math.PI * f / RATE, c = Math.cos(w), al = Math.sin(w) / (2 * q);
            return new Biquad((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + al, -2 * c, 1 - al);
        }

        static Biquad highpass(double f, double q) {
            double w = 2 * Math.PI * f / RATE, c = Math.cos(w), al = Math.sin(w) / (2 * q);
            return new Biquad((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + al, -2 * c, 1 - al);
        }

        static Biquad bandpass(double f, double q) {
            double w = 2 * Math.PI * f / RATE, c = Math.cos(w), al = Math.sin(w) / (2 * q);
            return new Biquad(al, 0, -al, 1 + al, -2 * c, 1 - al);
        }

        double process(double x) {
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = x;
            y2 = y1;
            y1 = y;
            return y;
        }
    }
}
