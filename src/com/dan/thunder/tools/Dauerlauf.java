package com.dan.thunder.tools;

import com.dan.thunder.camera.Ansichten;
import com.dan.thunder.data.BuiltinThunderRepository;
import com.dan.thunder.gl.Quality;
import com.dan.thunder.gl.Renderer;
import com.dan.thunder.model.Mood;
import com.dan.thunder.model.StormLevel;
import com.dan.thunder.model.ThunderData;
import com.dan.thunder.scene.SceneClock;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.GLRunnable;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Der Dauerlauf von Thunder in 3D (Phase 18c, aus Lighthouse Phase 16): die ganze Szene läuft ohne Fenster so lange wie
 * angegeben (Vorgabe 30 Minuten) mit der Regie des Gewitters. Alle 12 s geschieht etwas: eine Ansicht, ein Wechsel der
 * Stufe, Abend oder Nacht (weich), ein Klick in den Himmel, eine Krone, ein Einschlag in den Turm, eine andere
 * Qualität, Regen aus und an, „Sanfte Blitze“. Der Klang wird offline mitgerechnet (wie die Ausgabe, nur ohne
 * Lautsprecher). Gemessen werden Bilder je Sekunde, das schlechteste Bild, der Speicher nach dem Aufräumen und jede
 * Ausnahme in irgendeinem Faden.
 * <p>
 * Bestanden heißt: keine Ausnahme, kein Grafikfehler, der Speicher nach der fünften Minute nicht mehr als 25 % gewachsen,
 * kein Bild über 2 s (oder das 20-fache des Mittels, auf Mesa; die zwei Sekunden nach einem Wechsel der Qualität zählen
 * getrennt und dürfen bis 5 s brauchen), Blitze und Donner gezählt. Das Protokoll steht in
 * {@code docs/status/Dauerlauf_Phase18c.txt}; die letzte Zeile ist „Dauerlauf bestanden“ oder „Dauerlauf NICHT
 * bestanden“ mit dem Grund.
 * <pre>
 * Dauerlauf [Minuten] [Breite] [Datei]
 * </pre>
 */
public final class Dauerlauf {
    private Dauerlauf() { }

    static final List<String> FEHLER = new ArrayList<>();
    static volatile long frameStart;
    static final String[] EREIGNIS = {"Ansicht", "Stufe", "Klick in den Himmel", "Abend/Nacht", "Krone", "Ansicht", "Qualität",
            "Einschlag in den Turm", "Regen aus/an", "Sanfte Blitze", "Ansicht", "Kreisen"};

    public static void main(String[] a) throws Exception {
        double minuten = a.length > 0 ? Double.parseDouble(a[0].replace(',', '.')) : 30;
        int width = a.length > 1 ? Integer.parseInt(a[1]) : 1280;
        File out = new File(a.length > 2 ? a[2] : "docs/status/Dauerlauf_Phase18c.txt");
        out.getAbsoluteFile().getParentFile().mkdirs();
        int height = width * 9 / 16;
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> { synchronized (FEHLER) { FEHLER.add("Faden " + t.getName() + ": " + e); } e.printStackTrace(); });
        PrintWriter pw = new PrintWriter(Files.newBufferedWriter(out.toPath(), StandardCharsets.UTF_8));
        java.util.function.Consumer<String> say = s -> { System.out.println(s); pw.println(s); pw.flush(); };
        say.accept(String.format(Locale.GERMAN, "Thunder · Dauerlauf · %s · %.0f Minuten · %d×%d · %s", Renderer.VERSION, minuten, width, height,
                java.time.LocalDateTime.now().withNano(0)));
        say.accept("Rechner: " + System.getProperty("os.name") + " · Java " + System.getProperty("java.version") + " · " + Runtime.getRuntime().availableProcessors()
                + " Kerne · Xmx " + Runtime.getRuntime().maxMemory() / 1048576 + " MB");

        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, width, height);
        d.setAutoSwapBufferMode(false);
        SceneClock clock = new SceneClock();
        Renderer r = new Renderer(clock);
        ThunderData data = new BuiltinThunderRepository().load("OSTSEE");
        r.setDaten(data);
        List<StormLevel> stufen = data.levels();
        r.setLevel(stufen.get(1));
        r.setMood(Mood.ABEND, true);
        r.showHud = true;
        r.autoScale = true;
        r.targetFps = 60;
        r.setQuality(Quality.MITTEL);
        r.klang.offline = true;
        d.addGLEventListener(r);
        d.display();
        if (r.error() != null) { say.accept("FEHLER Grafik: " + r.error()); say.accept("Dauerlauf NICHT bestanden"); pw.close(); System.exit(2); }
        say.accept("Grafik: " + r.glInfo());
        float[] ton = new float[2 * 2048];
        Quality[] qs = Quality.values();
        Random rnd = new Random(2026);

        final Thread gl = Thread.currentThread();
        Thread waechter = new Thread(() -> {
            long gemeldet = 0;
            while (true) {
                try { Thread.sleep(200); } catch (InterruptedException e) { return; }
                long fs = frameStart;
                if (fs != 0 && fs != gemeldet && System.nanoTime() - fs > 800_000_000L) {
                    gemeldet = fs;
                    StringBuilder b = new StringBuilder("  Bild hängt seit 0,8 s in:");
                    StackTraceElement[] tr = gl.getStackTrace();
                    for (int i = 0; i < Math.min(8, tr.length); i++) b.append("\n    ").append(tr[i]);
                    say.accept(b.toString());
                }
            }
        }, "Wächter");
        waechter.setDaemon(true);
        waechter.start();
        // höchstens zwei Bilder in Arbeit, wie im Fenster der Tausch der Puffer (aus Lighthouse, Avatar-Phase 19)
        final long[] fences = new long[2];
        final int[] fenceI = {0};
        GLRunnable takt = drawable -> {
            GL4 g = drawable.getGL().getGL4();
            int k = fenceI[0]++ % fences.length;
            if (fences[k] != 0) { g.glClientWaitSync(fences[k], GL4.GL_SYNC_FLUSH_COMMANDS_BIT, 5_000_000_000L); g.glDeleteSync(fences[k]); }
            fences[k] = g.glFenceSync(GL4.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
            return true;
        };
        long start = System.nanoTime(), ende = start + (long) (minuten * 60e9), lastMinute = start, lastShot = start;
        long frames = 0;
        String lastEvent = "Start";
        long lastEventAt = start;
        double worst = 0, worstAll = 0, worstSwitch = 0;
        long switchUntil = 0;
        long heap5 = -1, heapEnd = -1;
        int minute = 0, ereignisse = 0, klicks = 0, kronen = 0, turm = 0, stufenwechsel = 0, qualitaet = 0, stufe = 1;
        boolean nacht = false;
        double sumFps = 0;
        int fpsN = 0;
        long lastNanos = System.nanoTime();
        while (System.nanoTime() < ende) {
            long t0 = System.nanoTime();
            frameStart = t0;
            d.display();
            d.invoke(true, takt);
            frameStart = 0;
            long t1 = System.nanoTime();
            double ms = (t1 - t0) / 1e6;
            frames++;
            if (frames > 30 && t0 < switchUntil) worstSwitch = Math.max(worstSwitch, ms);
            else if (frames > 30) { worst = Math.max(worst, ms); worstAll = Math.max(worstAll, ms); }
            if (frames > 30 && ms > 1000) say.accept(String.format(Locale.GERMAN, "  langes Bild %.0f ms, %.1f s nach „%s“", ms, (t0 - lastEventAt) / 1e9, lastEvent));
            // Klang wie die Ausgabe
            double dt = (t1 - lastNanos) / 1e9;
            lastNanos = t1;
            int n = Math.min(2048, (int) Math.round(dt * r.klang.s.sampleRate()));
            if (n > 0) r.klang.s.render(ton, n);
            if (r.error() != null) { synchronized (FEHLER) { FEHLER.add("Grafik: " + r.error()); } break; }
            if (t1 - lastShot > 12_000_000_000L) {
                lastShot = t1;
                int k = ereignisse++ % EREIGNIS.length;
                lastEvent = EREIGNIS[k];
                lastEventAt = t1;
                r.camera.autoOrbit = false;
                switch (k) {
                    case 0, 5, 10 -> r.placeView(Ansichten.ALLE.get(rnd.nextInt(Ansichten.ALLE.size())));
                    case 1 -> { stufe = (stufe + 1) % stufen.size(); r.setLevel(stufen.get(stufe)); stufenwechsel++; }
                    case 2 -> { r.klick(rnd.nextInt(width), rnd.nextInt(height / 2), width, height); klicks++; }
                    case 3 -> { nacht = !nacht; r.setMood(nacht ? Mood.NACHT : Mood.ABEND, false); }
                    case 4 -> { r.blitzTaste('K'); kronen++; }
                    case 6 -> { r.setQuality(qs[rnd.nextInt(qs.length)]); qualitaet++; switchUntil = System.nanoTime() + 2_000_000_000L; }
                    case 7 -> { r.blitzTaste('T'); turm++; }
                    case 8 -> r.gewitter.regenAn = !r.gewitter.regenAn;
                    case 9 -> r.gewitter.setSanft(!r.gewitter.sanft());
                    case 11 -> { r.placeView(Ansichten.taste(1)); r.camera.setMode(com.dan.thunder.camera.Camera.Mode.KREISEN); r.camera.autoOrbit = true; r.camera.orbitSpeed = 8; }
                    default -> { }
                }
            }
            if (t1 - lastMinute > 60_000_000_000L) {
                lastMinute = t1;
                minute++;
                System.gc();
                Thread.sleep(50);
                long heap = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576;
                if (minute == 5) heap5 = heap;
                heapEnd = heap;
                double fps = r.fps();
                sumFps += fps;
                fpsN++;
                say.accept(String.format(Locale.GERMAN, "Minute %2d · %s · %s · %.0f Bilder/s · Grafikkarte %.1f ms · Prozessor %.1f ms · schlechtestes Bild %.0f ms · Auflösung %.0f %% (%s) · Speicher %d MB · %d Blitze, %d Donner · Klang %.1f %%",
                        minute, clock.label(), r.gewitter.level() == null ? "–" : r.gewitter.level().name(), fps, r.gpuMs(), r.cpuMs(), worst, r.renderScale * 100, r.quality().label, heap,
                        r.gewitter.blitz.flashes(), r.klang.donner, r.klang.s.msPerSecond() / 10));
                worst = 0;
            }
        }
        double sek = (System.nanoTime() - start) / 1e9;
        int blitze = r.gewitter.blitz.flashes(), donner = r.klang.donner;
        say.accept(String.format(Locale.GERMAN, "Ende nach %.1f Minuten: %d Bilder, im Mittel %.1f Bilder/s, schlechtestes Bild %.0f ms, %d Ereignisse, %d Blitze (%d Erdblitze, %d in der Wolke, %d Kronen, %d Wetterleuchten), %d Donner, "
                        + "%d Klicks, %d Kronen von Hand, %d Einschläge in den Turm, %d Stufenwechsel, %d Qualitätswechsel, Auflösung %d× geregelt, Speicher Minute 5: %d MB, Ende: %d MB, größter Ruck beim Qualitätswechsel %.0f ms",
                sek / 60, frames, fpsN > 0 ? sumFps / fpsN : frames / sek, worstAll, ereignisse, blitze, r.gewitter.erdblitze, r.gewitter.wolkenblitze, r.gewitter.kronen, r.gewitter.wetterleuchten,
                donner, klicks, kronen, turm, stufenwechsel, qualitaet, r.scaleSteps(), heap5, heapEnd, worstSwitch));
        List<String> gruende = new ArrayList<>();
        synchronized (FEHLER) { gruende.addAll(FEHLER); }
        if (heap5 > 0 && heapEnd > heap5 * 1.25 + 64) gruende.add(String.format(Locale.GERMAN, "Speicher wächst: %d MB nach Minute 5, %d MB am Ende", heap5, heapEnd));
        double mean = sek / Math.max(1, frames) * 1000;
        if (worstAll > Math.max(2000, 20 * mean)) gruende.add(String.format(Locale.GERMAN, "ein Bild brauchte %.0f ms (Mittel %.0f ms)", worstAll, mean));
        if (worstSwitch > 5000) gruende.add(String.format(Locale.GERMAN, "ein Qualitätswechsel brauchte %.0f ms", worstSwitch));
        if (minute >= 1 && blitze < minute * 3) gruende.add("zu wenige Blitze: " + blitze + " in " + minute + " Minuten");
        if (minute >= 2 && donner == 0) gruende.add("kein Donner");
        if (minute < 1) gruende.add("keine volle Minute gelaufen");
        for (String g : gruende) say.accept("FEHLER " + g);
        say.accept(gruende.isEmpty() ? "Dauerlauf bestanden" : "Dauerlauf NICHT bestanden (" + gruende.size() + " Gründe)");
        pw.close();
        r.klang.dispose();
        System.exit(gruende.isEmpty() ? 0 : 1);
    }
}
