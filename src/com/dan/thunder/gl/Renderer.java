package com.dan.thunder.gl;

import com.dan.cloud.Clouds;
import com.dan.cloud.gl.CloudGl;
import com.dan.sky.Sky;
import com.dan.sky.gl.SkyGl;
import com.dan.thunder.camera.Ansichten;
import com.dan.thunder.camera.Camera;
import com.dan.thunder.model.Mood;
import com.dan.thunder.model.StormLevel;
import com.dan.thunder.scene.Bauwerke;
import com.dan.thunder.scene.Brandung;
import com.dan.thunder.scene.Gelaende;
import com.dan.thunder.scene.SceneClock;
import com.dan.thunder.scene.Site;
import com.dan.thunder.scene.Sun;
import com.dan.thunder.wiring.Wiring;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Thunder in 3D (Phase 18a, F30 A, F51 A): die Engine aus Sylt, entschlackt für den Ostseestrand, mit den Packages aus
 * {@code Renderer.jar}. Je Bild:
 * <ol>
 * <li><b>Himmel</b>: {@code com.dan.sky} über 54,0° N (Sonne, Mond, Sterne, Streuung) in einen HDR-Puffer (RGBA16F,
 * umgekehrte 32F-Tiefe), dazu das Rundumbild mit Wolken (Spiegelung, Dunst) und die Schattenkarte der Wolken.</li>
 * <li><b>Gelände</b>: Strand, Düne, Landzunge, Seegrund aus {@link Gelaende} (gerechnet).</li>
 * <li><b>Bauten</b>: Leuchtturm, Seebrücke, Strandkörbe ({@link Bauwerke}, {@code bauten.vert/frag} aus Sylt).</li>
 * <li><b>Meer</b>: {@code com.dan.ocean} mit dem Gelände als Grund, vor dem Strand das Netz der Brecher aus
 * {@code com.dan.surf} ({@link Brandung}, {@code Surf.ostsee()}).</li>
 * <li><b>Wolken</b>: {@code com.dan.cloud} (Cumulonimbus) nach der Szene mit ihrer Tiefe.</li>
 * <li><b>Nachbearbeitung</b>: Belichtung aus der Augenanpassung, Filmkurve; das HUD.</li>
 * </ol>
 * Blitze, Glutkern, Regen und Klang kommen in Phase 18b, Bedienung und Datenbank in 18c.
 */
public final class Renderer implements GLEventListener {
    public static final int MIN_MAJOR = 4, MIN_MINOR = 3;
    public static final String VERSION = "Phase 18a";
    public static final String[] PASSES = {"Himmel", "Gelände", "Bauten", "Meer", "Wolken", "Nachbearbeitung", "HUD"};
    private static final int MARKS = PASSES.length + 1;
    static final double NEAR = 0.2, FAR = 60_000, FOG_DISTANCE = 30_000;

    public final SceneClock clock;
    public final Camera camera = new Camera();
    public final Wiring wiring;
    public final Wetter wetter = new Wetter();
    public final Meer meer = new Meer();
    public final Brandung brandung = new Brandung();
    public final Bauwerke bauwerke = new Bauwerke();
    final BrandungGl brandungGl = new BrandungGl();
    final GelaendeGl gelaendeGl = new GelaendeGl();
    final BautenGl bautenGl = new BautenGl();
    private final Belichtung belichtung = new Belichtung();
    /** Ebenen zum Abschalten. */
    public volatile boolean showClouds = true, showOcean = true, showBrandung = true, showBauten = true;
    public volatile boolean bildMessung = true;
    /** Anpassung des Auges (0 fest … 1 voll); unter dem Gewitter weniger als in Sylt, damit es dunkel bleibt [G]. */
    public volatile double adaptation = 0.85;
    public static final double BILD_FOLGT = 0.75, BILD_GAIN = 2.0, STORM_ADAPT = 0.6;
    private double adaptLum = -1, viewLum = -1, viewTimer, cloudShade = 1;
    private boolean frameSky;

    private volatile Quality quality = Quality.MITTEL;
    public volatile double renderScale = quality.renderScale;
    public volatile boolean autoScale = true;
    public volatile double targetFps = 60;
    public static final double MIN_SCALE = 0.5;
    private long scaleNanos;
    private int scaleSteps;
    public volatile boolean showHud = true, hudFull;
    public volatile boolean frozen;
    /** Belichtung von Hand in Blenden (dazu die der Stimmung, {@link #stimmungEv()}). */
    public volatile double exposureBias;
    /** Belichtung der Stimmung in Blenden: abends dunkler, nachts heller, wie die Bilder in docs/ [G]. */
    public static final double ABEND_EV = -0.7, NACHT_EV = 0.6;

    /** Blenden der Stimmung nach der Sonnenhöhe: über −4° wie „Abend“, unter −10° wie „Nacht“, dazwischen gleitend. */
    public double stimmungEv() { return ABEND_EV + (NACHT_EV - ABEND_EV) * smooth(-4, -10, clock.sun()[0]); }
    public volatile boolean photoRequest;
    public volatile Consumer<BufferedImage> grabRequest;
    public volatile File photoDir;
    public volatile String photoName;

    /** Weicher Wechsel der Stimmung: Uhrzeit von → nach, Fortschritt 0..1 (1,5 s). */
    private double moodFrom = Double.NaN, moodTo = Double.NaN, moodT = 1;
    public static final double MOOD_SEC = 1.5;

    private final Shader skyShader = new Shader("Himmel", null, "fullscreen.vert", "sky.frag"),
            gelaendeShader = new Shader("Gelände", null, "gelaende.vert", "gelaende.frag"),
            post = new Shader("Nachbearbeitung", null, "fullscreen.vert", "post.frag"),
            overlay = new Shader("Überlagerung", null, "fullscreen.vert", "overlay.frag"),
            lumShader = new Shader("Belichtung", null, "fullscreen.vert", "lum.frag"),
            bautenShader = new Shader("Bauten", null, "bauten.vert", "bauten.frag");
    private final Shader[] all = {skyShader, gelaendeShader, post, overlay, lumShader, bautenShader};
    private final HudOverlay hud = new HudOverlay();
    private int emptyVao, hdrTex, depthTex, hdrFbo, hdrColorFbo, hdrW, hdrH, surfW = 1, surfH = 1, defaultFbo;
    private boolean reversedZ, ready;
    private final int[][] stamp = new int[2][MARKS];
    private final double[] passMs = new double[MARKS - 1];
    private int frame;
    private long lastNanos, statsNanos;
    private int statsFrames;
    private double fps, gpuMs, cpuMs, worstMs;
    private volatile String glInfo = "", error;
    private volatile ComputeProbe.Result compute;
    private volatile boolean reloadShaders;
    private Consumer<String> statusListener = s -> { };
    private Consumer<String> errorListener = s -> { };
    private volatile String photoMsg = "", meldung = "";
    private volatile long photoMsgUntil, meldungUntil;

    public Renderer(SceneClock clock) {
        this.clock = clock;
        this.wiring = new Wiring(clock);
        wetter.sky.latDeg = Site.LAT;
        wetter.sky.lonDeg = Site.LON;
        wetter.sky.epochMillis = clock.epochMillis();
        Thread nt = new Thread(() -> { com.dan.cloud.Noise.base(); com.dan.cloud.Noise.detail(); com.dan.cloud.Noise.weather(); }, "wolken-rauschen");
        nt.setDaemon(true);
        nt.start();
        setQuality(quality);
        camera.waterLevel = 0;
        camera.groundLevel = Gelaende.height(camera.eye[0], camera.eye[2]);
        camera.snap();
    }

    public void setStatusListener(Consumer<String> l) { statusListener = l == null ? s -> { } : l; }
    public void setErrorListener(Consumer<String> l) { errorListener = l == null ? s -> { } : l; }
    public String error() { return error; }
    public String glInfo() { return glInfo; }
    public Quality quality() { return quality; }
    public double fps() { return fps; }
    public double gpuMs() { return gpuMs; }
    public double cpuMs() { return cpuMs; }
    public long frames() { return frame; }
    public double takeWorstFrameMs() { double w = worstMs; worstMs = 0; return w; }
    public double fovy() { return camera.fovy; }
    public void requestShaderReload() { reloadShaders = true; }
    /** Kein Kino in Thunder (die Hülle aus Sylt fragt danach). */
    public boolean kinoLaeuft() { return false; }
    public void kinoVerlassen() { }

    public String[][] shaderFiles() {
        String[][] f = new String[all.length][];
        for (int i = 0; i < f.length; i++) f[i] = all[i].files();
        return f;
    }

    public void setQuality(Quality q) {
        quality = q;
        renderScale = q.renderScale;
        meer.ocean.quality = q.ocean;
        wetter.clouds.quality = q.clouds;
        brandung.reihen = q.surfRows;
    }

    /** Die Gewitterstufe aus TH_STORM_LEVEL (Wind, See, Wolken). */
    public void setLevel(StormLevel l) {
        wiring.level = l;
        hudMeldung("Stufe " + (l == null ? "?" : l.name()));
    }

    /**
     * Abend oder Nacht (F48 A): die Uhr wandert weich (1,5 s) auf 21:20 bzw. 23:30 Uhr; sofort für Standbilder.
     */
    public void setMood(Mood m, boolean sofort) {
        wiring.mood = m;
        double ziel = m == Mood.NACHT ? SceneClock.NACHT : SceneClock.ABEND;
        if (sofort) {
            clock.set(clock.date(), ziel);
            moodT = 1;
            wetter.sky.epochMillis = clock.epochMillis();
            readapt();
        } else {
            moodFrom = clock.hour();
            moodTo = ziel;
            moodT = 0;
        }
    }

    /** Stellt die Kamera auf eine Ansicht. */
    public void placeView(Ansichten.Ansicht a) {
        camera.stopFahrt();
        a.stellen(camera);
        hudMeldung("Ansicht " + a.key() + ": " + a.name());
    }

    /** Für Standbilder: das Stück der Brandung jetzt bauen. */
    public void brandungJetzt() {
        wiring.feed(meer.ocean);
        brandung.jetzt(wiring.seaHs(), wiring.seaTp());
    }

    public void hudMeldung(String text) {
        meldung = text;
        meldungUntil = System.currentTimeMillis() + 5000;
    }

    public double exposure() {
        if (wetter.skyOk && adaptLum > 0) return Sky.State.exposureFor(adaptLum, adaptation) * Math.pow(2, exposureBias + stimmungEv());
        double b = Sun.skyBrightness(clock.sun()[0]);
        return 0.6 * Math.pow(b, -0.85) * Math.pow(2, exposureBias + stimmungEv());
    }

    public void readapt() {
        viewLum = -1;
        adaptLum = -1;
        belichtung.reset();
        if (wetter.skyOk) adapt(wetter.state(), 1);
    }

    private void adapt(Sky.State st, double dt) {
        viewTimer -= dt;
        if (viewLum <= 0 || viewTimer <= 0) {
            viewLum = st.viewLuminance(camera.fwd.clone(), new double[]{0, 1, 0}, fovy(), surfW / (double) Math.max(1, surfH));
            viewTimer = 0.2;
        }
        double gemessen = bildMessung ? belichtung.lum() : -1;
        double target = gemessen > 0
                ? Math.pow(Math.max(1e-14, st.adaptation), 1 - BILD_FOLGT) * Math.pow(Math.max(1e-14, gemessen * BILD_GAIN), BILD_FOLGT)
                : Math.sqrt(Math.max(1e-14, st.adaptation) * Math.max(1e-14, viewLum))
                * (wetter.cloudsOk && showClouds ? Math.pow(wetter.clouds.brightnessFactor(), STORM_ADAPT) : 1);
        if (adaptLum <= 0 || frozen || dt > 0.5) adaptLum = target;
        else adaptLum = Math.exp(Math.log(adaptLum) + (Math.log(target) - Math.log(adaptLum)) * Math.min(1, dt / 1.0));
    }

    // ------------------------------------------------------------------ GL

    @Override
    public void init(GLAutoDrawable d) {
        initGl(d.getGL().getGL4(), d.getContext().getGLVersionNumber().getMajor(), d.getContext().getGLVersionNumber().getMinor(),
                d.getContext().getDefaultDrawFramebuffer());
    }

    public void initGl(GL4 gl, int major, int minor, int targetFbo) {
        String rend = gl.glGetString(GL.GL_RENDERER), vendor = gl.glGetString(GL.GL_VENDOR), version = gl.glGetString(GL.GL_VERSION);
        glInfo = rend + " · OpenGL " + major + "." + minor;
        System.out.println("Thunder: " + vendor + " · " + rend + " · " + version);
        if (major < MIN_MAJOR || (major == MIN_MAJOR && minor < MIN_MINOR)) {
            fail("Die Grafikkarte meldet OpenGL " + major + "." + minor + " (" + rend + "). Thunder braucht OpenGL " + MIN_MAJOR + "." + MIN_MINOR
                    + ".\nBitte den Grafiktreiber aktualisieren.");
            return;
        }
        gl.setSwapInterval(1);
        defaultFbo = targetFbo;
        reversedZ = (major > 4 || minor >= 5) || gl.isExtensionAvailable("GL_ARB_clip_control");
        if (reversedZ) gl.glClipControl(GL4.GL_LOWER_LEFT, GL4.GL_ZERO_TO_ONE);
        try {
            for (Shader s : all) s.build(gl);
        } catch (RuntimeException e) {
            fail(e.getMessage());
            return;
        }
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        emptyVao = v[0];
        for (int[] s : stamp) gl.glGenQueries(MARKS, s, 0);
        compute = ComputeProbe.run(gl);
        System.out.println("Thunder: " + compute.line());
        gelaendeGl.init(gl);
        System.out.println("Thunder: " + Gelaende.describe() + " · " + gelaendeGl.info);
        wetter.init(gl, errorListener);
        wiring.cloudsDrawn = wetter.cloudsOk;
        meer.init(gl, compute.ok(), errorListener);
        brandungGl.init(gl);
        bautenGl.init(gl);
        bautenGl.upload(gl, BautenGl.FEST, bauwerke.netz);
        System.out.println("Thunder: " + bauwerke.describe());
        belichtung.init(gl);
        lastNanos = System.nanoTime();
        statsNanos = lastNanos;
        camera.snap();
        ready = true;
    }

    @Override
    public void reshape(GLAutoDrawable d, int x, int y, int w, int h) {
        surfW = Math.max(1, d.getSurfaceWidth());
        surfH = Math.max(1, d.getSurfaceHeight());
    }

    /** Uhr, Wetter, See, Brandung und Kamera um dt weiterrechnen. */
    public void step(double dt) {
        clock.step(dt);
        if (moodT < 1) {
            moodT = Math.min(1, moodT + dt / MOOD_SEC);
            double k = moodT * moodT * (3 - 2 * moodT);
            clock.set(clock.date(), moodFrom + (moodTo - moodFrom) * k);
        }
        camera.groundLevel = Gelaende.height(camera.eye[0], camera.eye[2]);
        camera.waterLevel = 0;
        camera.targetGround = Math.max(Gelaende.height(camera.tx, camera.tz), 0);
        camera.step(dt);
        boolean cloudsShown = wetter.cloudsOk && showClouds;
        wiring.cloudsDrawn = cloudsShown;
        wiring.feed(wetter.sky, camera.eye[1]);
        wiring.feed(wetter.clouds);
        wiring.feed(meer.ocean);
        meer.ocean.step(dt);
        brandung.an = showBrandung && showOcean;
        brandung.schritt(dt, wiring.seaHs(), wiring.seaTp());
        if (wetter.skyOk) {
            Sky.State st = wetter.sky.step(dt);
            wetter.clouds.step(dt);
            cloudShade = cloudsShown ? Clouds.shadow(wetter.clouds.snapshot(), camera.eye[0], camera.eye[2], Wetter.keyDir(st)) : 1;
            adapt(st, dt);
        }
    }

    @Override
    public void display(GLAutoDrawable d) {
        displayGl(d.getGL().getGL4(), d.getSurfaceWidth(), d.getSurfaceHeight(), d.getContext().getDefaultDrawFramebuffer());
    }

    public void displayGl(GL4 gl, int width, int height, int targetFbo) {
        if (!ready) {
            gl.glClearColor(0.04f, 0.05f, 0.08f, 1);
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);
            return;
        }
        if (reloadShaders) {
            reloadShaders = false;
            try {
                for (Shader s : all) s.build(gl);
                hudMeldung("Shader neu geladen");
            } catch (RuntimeException e) {
                System.err.println(e.getMessage());
                errorListener.accept(e.getMessage());
            }
        }
        defaultFbo = targetFbo;
        readMarks(gl);
        long now = System.nanoTime();
        double rawMs = (now - lastNanos) / 1e6;
        if (frame > 2 && rawMs > worstMs) worstMs = rawMs;
        double dt = Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        if (!frozen) step(dt);
        surfW = Math.max(1, width);
        surfH = Math.max(1, height);
        regulateScale(now);
        int w = Math.max(64, (int) Math.round(surfW * renderScale)), h = Math.max(64, (int) Math.round(surfH * renderScale));
        if (w != hdrW || h != hdrH) resizeTargets(gl, w, h);
        renderFrame(gl, w, h);
        if (photoRequest) { photoRequest = false; takePhoto(gl, surfW, surfH); }
        Consumer<BufferedImage> gr = grabRequest;
        if (gr != null) { grabRequest = null; try { gr.accept(readDefault(gl, surfW, surfH)); } catch (RuntimeException e) { System.err.println("Thunder: Bild nicht abgeholt: " + e); } }
        cpuMs = cpuMs * 0.95 + (System.nanoTime() - now) / 1e6 * 0.05;
        frame++;
        stats(now, w, h);
    }

    void renderFrame(GL4 gl, int w, int h) {
        mark(gl, 0);
        frameSky = wetter.skyOk;
        Sky.State st = frameSky ? wetter.state() : null;
        if (frameSky && adaptLum <= 0) adapt(st, 1);
        double pre = frameSky ? exposure() : 1;
        double[] sp = clock.sun();
        double[] sun = frameSky ? st.sunDir : Sun.direction(sp[0], sp[1]);
        float[] proj = Mat4.perspective(fovy(), w / (double) h, NEAR, FAR, reversedZ);
        float[] vp = Mat4.mul(proj, camera.view());
        float[] inv = Mat4.invert(vp);
        boolean cloudsNow = frameSky && wetter.cloudsOk && showClouds;
        Clouds.Snapshot cs = wetter.clouds.snapshot();
        CloudGl.Light cl = null;

        // 1 Himmel und Rundumbild mit Wolken
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        gl.glClearDepth(reversedZ ? 0 : 1);
        gl.glClearColor(0, 0, 0, 1);
        gl.glDepthMask(true);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        gl.glBindVertexArray(emptyVao);
        if (frameSky) {
            wetter.skyGl.update(gl, st);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
            SkyGl.View sv = new SkyGl.View();
            sv.viewProj = vp;
            sv.invViewProj = inv;
            sv.reversedZ = reversedZ;
            sv.width = w;
            sv.height = h;
            sv.fovyDeg = fovy();
            sv.preExposure = pre;
            sv.time = clock.t();
            wetter.skyGl.draw(gl, st, sv);
            if (wetter.skyCheck.isEmpty() && frame > 30) wetter.skyCheck = wetter.skyGl.selfCheck(gl, st);
            gl.glBindVertexArray(emptyVao);
            if (cloudsNow) {
                cl = wetter.cloudLight(st, pre);
                wetter.cloudGl.update(gl, cs, cl, camera.eye.clone());
                if (wetter.cloudCheck.isEmpty() && frame > 40) wetter.cloudCheck = wetter.cloudGl.selfCheck(gl, cs, cl);
            }
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, wetter.panoFbo);
            gl.glViewport(0, 0, Wetter.PANO_W, Wetter.PANO_H);
            wetter.skyGl.drawPanorama(gl, st, pre);
            if (cloudsNow) wetter.cloudGl.drawPanorama(gl, cs, cl, camera.eye.clone());
            gl.glBindTexture(GL.GL_TEXTURE_2D, wetter.panoTex);
            gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
            gl.glBindVertexArray(emptyVao);
        } else {
            common(gl, skyShader, vp, inv, sun);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        }
        mark(gl, 1);

        // 2 Gelände
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(true);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        common(gl, gelaendeShader, vp, inv, sun);
        cloudShadow(gl, gelaendeShader, cloudsNow);
        gelaendeShader.set(gl, "uWet", 0f);
        gelaendeGl.draw(gl);
        gl.glBindVertexArray(emptyVao);
        mark(gl, 2);

        // 3 Bauten
        if (showBauten && bautenGl.ok) {
            common(gl, bautenShader, vp, inv, sun);
            cloudShadow(gl, bautenShader, cloudsNow);
            bautenShader.set(gl, "uNight", (float) smooth(-1, -6, sp[0]));
            double k = (frameSky ? pre : exposure()) / Sky.CD_PER_UNIT;
            bautenShader.set(gl, "uWarm", (float) (15 * k), (float) (15 * k * 0.72), (float) (15 * k * 0.42));
            // Laternen: Leuchtturm (K) und Seebrücke (Q) aus in 18a (ab 18b mit beacon)
            double[] lt = bauwerke.laterneTurm, lb = bauwerke.laterneBruecke;
            bautenShader.set(gl, "uTurmK", (float) lt[0], (float) lt[2], (float) lt[1], 0.3f);
            bautenShader.set(gl, "uTurmQ", (float) lb[0], (float) lb[2], (float) lb[1], 0.1f);
            for (String t : new String[]{"K", "Q"}) {
                bautenShader.set(gl, "uLens" + t, 0f, 0f, 0f);
                bautenShader.set(gl, "uGlow" + t, 0f, 0f, 0f);
            }
            bautenShader.set(gl, "uSpotPos", 0f, 0f, 0f, 0f);
            bautenGl.draw(gl, true, false, false, false);
            gl.glBindVertexArray(emptyVao);
        }
        mark(gl, 3);

        // 4 Meer und Brecher
        if (showOcean && meer.ok) {
            gl.glEnable(GL.GL_DEPTH_TEST);
            gl.glDepthMask(true);
            boolean netz = brandung.stueck() != null;
            meer.draw(gl, vp, camera.eye, reversedZ, st, pre, frameSky ? wetter.panoTex : 0, cloudsNow ? wetter.cloudGl.shadowTexture() : 0,
                    cloudsNow ? wetter.cloudGl.shadowRect() : new double[]{0, 0, 1}, sun, sunLight(sp[0]), FOG_DISTANCE,
                    ol -> { if (netz) brandungGl.feed(ol, brandung); });
            gl.glBindVertexArray(emptyVao);
            if (netz) brandungGl.draw(gl, vp, camera.eye, reversedZ, brandung, st, pre, sun, wetter, showClouds, sunLight(sp[0]), FOG_DISTANCE, clock.t(), camera.heading());
            if (meer.check.isEmpty() && frame > 30) meer.check = meer.oceanGl.selfCheck(gl, meer.ocean);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
            gl.glBindVertexArray(emptyVao);
        }
        mark(gl, 4);

        // 5 Wolken nach der Szene mit ihrer Tiefe
        gl.glDisable(GL.GL_DEPTH_TEST);
        if (cloudsNow) {
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrColorFbo);
            gl.glViewport(0, 0, w, h);
            CloudGl.View cv = new CloudGl.View();
            cv.viewProj = vp;
            cv.invViewProj = inv;
            cv.eye = camera.eye.clone();
            cv.reversedZ = reversedZ;
            cv.width = w;
            cv.height = h;
            cv.depthTexture = depthTex;
            wetter.cloudGl.draw(gl, cs, cl, cv);
            gl.glBindVertexArray(emptyVao);
        }
        if (frameSky && bildMessung) {
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrColorFbo);
            belichtung.step(gl, lumShader, hdrTex, emptyVao, pre, frozen);
        }
        mark(gl, 5);

        // 6 Nachbearbeitung
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, defaultFbo);
        gl.glViewport(0, 0, surfW, surfH);
        post.use(gl);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, hdrTex);
        post.set(gl, "uHdr", 0);
        post.set(gl, "uExposure", frameSky ? 1f : (float) exposure());
        post.set(gl, "uScotopic", (float) (frameSky ? Wetter.scotopic(st) : 0));
        gl.glBindVertexArray(emptyVao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        mark(gl, 6);

        // 7 HUD
        gl.glFlush();
        if (showHud) {
            hud.refresh(gl, 250, this::paintHud, frozen);
            hud.draw(gl, overlay, emptyVao, 12, Math.max(0, surfH - hud.height - 12), surfW, surfH);
        }
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        mark(gl, 7);
    }

    private void cloudShadow(GL4 gl, Shader s, boolean cloudsNow) {
        gl.glActiveTexture(GL.GL_TEXTURE0 + 13);
        gl.glBindTexture(GL.GL_TEXTURE_2D, cloudsNow ? wetter.cloudGl.shadowTexture() : 0);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        s.set(gl, "uCloudShadow", 13);
        double[] cr = cloudsNow ? wetter.cloudGl.shadowRect() : new double[]{0, 0, 1};
        s.set(gl, "uCloudRect", (float) cr[0], (float) cr[1], (float) Math.max(1, cr[2]), cloudsNow ? 1f : 0f);
    }

    public static double[] sunLight(double elevDeg) {
        double e = Math.sin(Math.toRadians(elevDeg));
        double vis = smooth(-0.015, 0.01, e), t = smooth(0.02, 0.35, e), f = 2.6 * vis * smooth(-0.02, 0.12, e);
        return new double[]{f, (0.55 + 0.40 * t) * f, (0.28 + 0.60 * t) * f};
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    private void common(GL4 gl, Shader s, float[] vp, float[] inv, double[] sun) {
        s.use(gl);
        boolean sm = frameSky && s != skyShader;
        s.set(gl, "uSkyMode", sm ? 1 : 0);
        if (sm) {
            Sky.State st = wetter.state();
            double pre = exposure();
            gl.glActiveTexture(GL.GL_TEXTURE7);
            gl.glBindTexture(GL.GL_TEXTURE_2D, wetter.panoTex);
            gl.glActiveTexture(GL.GL_TEXTURE0);
            s.set(gl, "uPano", 7);
            s.set(gl, "uSunColor", Wetter.scale(st.sunColor, pre));
            s.set(gl, "uMoonColor", Wetter.scale(st.moonColor, pre));
            s.set(gl, "uMoonDir", st.moonDir);
            s.set(gl, "uFogDist", (float) Wetter.fogDistance(st));
            s.set(gl, "uPre", (float) pre);
            s.set(gl, "uSkyAmb", Wetter.scale(wetter.ambient(st, showClouds), pre / Math.PI));
        }
        s.mat(gl, "uVP", vp);
        s.mat(gl, "uInvVP", inv);
        s.set(gl, "uCam", camera.eye);
        s.set(gl, "uSun", sun);
        s.set(gl, "uTime", (float) (clock.t() % 10_000));
        s.set(gl, "uLevel", 0f);
        s.set(gl, "uReversed", reversedZ ? 1 : 0);
    }

    private void resizeTargets(GL4 gl, int w, int h) {
        Gpu.deleteFbos(gl, hdrFbo, hdrColorFbo);
        Gpu.deleteTextures(gl, hdrTex, depthTex);
        hdrTex = Gpu.texture(gl, w, h, GL.GL_RGBA16F, false);
        depthTex = Gpu.texture(gl, w, h, GL4.GL_DEPTH_COMPONENT32F, false);
        gl.glBindTexture(GL.GL_TEXTURE_2D, depthTex);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        hdrFbo = Gpu.fbo(gl, hdrTex, depthTex);
        hdrColorFbo = Gpu.fbo(gl, hdrTex, 0);
        hdrW = w;
        hdrH = h;
    }

    // ------------------------------------------------------------------ Zeitmessung

    private void mark(GL4 gl, int i) { gl.glQueryCounter(stamp[frame & 1][i], GL4.GL_TIMESTAMP); }

    private void readMarks(GL4 gl) {
        if (frame < 2) return;
        int o = (frame - 1) & 1;
        int[] av = new int[1];
        gl.glGetQueryObjectiv(stamp[o][MARKS - 1], GL4.GL_QUERY_RESULT_AVAILABLE, av, 0);
        if (av[0] == 0) return;
        long[] t = new long[MARKS];
        for (int i = 0; i < MARKS; i++) gl.glGetQueryObjecti64v(stamp[o][i], GL4.GL_QUERY_RESULT, t, i);
        for (int i = 0; i < MARKS - 1; i++) passMs[i] = passMs[i] * 0.9 + Math.max(0, t[i + 1] - t[i]) / 1e6 * 0.1;
        gpuMs = gpuMs * 0.9 + Math.max(0, t[MARKS - 1] - t[0]) / 1e6 * 0.1;
    }

    public String passTimes() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < passMs.length; i++)
            sb.append(i > 0 ? " · " : "").append(PASSES[i]).append(' ').append(String.format(Locale.GERMAN, "%.2f", passMs[i]));
        return sb.toString();
    }

    private void stats(long now, int w, int h) {
        statsFrames++;
        if (now - statsNanos < 500_000_000L) return;
        fps = statsFrames / ((now - statsNanos) / 1e9);
        statsFrames = 0;
        statsNanos = now;
        statusListener.accept(statusLine(w, h));
    }

    private void regulateScale(long now) {
        if (!autoScale || frame < 60 || now - scaleNanos < 1_500_000_000L) return;
        scaleNanos = now;
        double budget = 1000 / Math.max(10, targetFps), top = quality.renderScale, s = renderScale;
        double want = s * Math.sqrt(budget * 0.9 / Math.max(0.1, gpuMs));
        double next = s;
        if (gpuMs > budget * 1.05) next = Math.max(MIN_SCALE, Math.max(want, s - 0.15));
        else if (gpuMs < budget * 0.7 && s < top) next = Math.min(top, Math.min(want, s + 0.05));
        next = Math.round(next * 20) / 20.0;
        if (Math.abs(next - s) >= 0.045) { renderScale = next; scaleSteps++; }
    }

    public int scaleSteps() { return scaleSteps; }

    public String statusLine(int w, int h) {
        String s = String.format(Locale.GERMAN, "%s · %.0f Bilder/s · Grafikkarte %.2f ms · Prozessor %.2f ms · %d×%d (%s%s) · %s",
                clock.label(), fps, gpuMs, cpuMs, w, h, quality.label, autoScale ? String.format(Locale.GERMAN, ", %.0f %%", renderScale * 100) : "", glInfo);
        long nowMs = System.currentTimeMillis();
        if (nowMs < meldungUntil) s = meldung + " · " + s;
        if (nowMs < photoMsgUntil) s = photoMsg + " · " + s;
        return s;
    }

    // ------------------------------------------------------------------ HUD

    BufferedImage paintHud() {
        String[] lines = hudFull ? hudLines() : hudShort();
        Font font = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        java.awt.FontMetrics fm = pg.getFontMetrics(font);
        int tw = 0;
        for (String l : lines) tw = Math.max(tw, fm.stringWidth(l.startsWith("!") ? l.substring(1) : l));
        pg.dispose();
        int w = Math.max(200, tw + 22), lh = 17, h = 16 + lines.length * lh;
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(10, 14, 24, 190));
        g.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
        g.setColor(new Color(120, 150, 255, 225));   // Blitzblau
        g.setStroke(new BasicStroke(2f));
        g.drawLine(8, 1, 60, 1);
        g.setColor(new Color(255, 154, 80, 225));    // Glutkern
        g.drawLine(62, 1, 80, 1);
        g.setFont(font);
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            boolean warn = l.startsWith("!");
            if (warn) l = l.substring(1);
            g.setColor(i == 0 ? new Color(233, 236, 240) : warn ? new Color(224, 163, 63) : new Color(186, 192, 204));
            g.drawString(l, 10, 18 + i * lh);
        }
        g.dispose();
        return im;
    }

    String[] hudShort() {
        java.util.List<String> l = new java.util.ArrayList<>();
        String q = autoScale ? String.format(Locale.GERMAN, "%s %.0f %%", quality.label, renderScale * 100) : quality.label;
        l.add(String.format(Locale.GERMAN, "%.0f Bilder/s · Grafikkarte %.1f ms · Prozessor %.1f ms · %s %d×%d", fps, gpuMs, cpuMs, q, hdrW, hdrH));
        l.add(passTimes());
        double[] sp = clock.sun();
        l.add(String.format(Locale.GERMAN, "%s · Sonne %.1f° aus %03.0f° · %s", clock.label(), sp[0], sp[1], wiring.mood.getLabel()));
        l.add(cameraLine() + " · H: volles HUD");
        if (System.currentTimeMillis() < meldungUntil) l.add(meldung);
        if (error != null) l.add("!" + error.split("\n")[0]);
        return l.toArray(new String[0]);
    }

    public String cameraLine() {
        return String.format(Locale.GERMAN, "%s · Auge %.1f m über dem Wasser, %.1f m über Grund · Blick %03.0f°", camera.modeName(), camera.eye[1],
                camera.eye[1] - Math.max(camera.groundLevel, 0), camera.heading());
    }

    public String[] hudLines() {
        double[] sp = clock.sun();
        ComputeProbe.Result c = compute;
        return new String[]{
                String.format(Locale.GERMAN, "%.0f Bilder/s   Grafikkarte %.2f ms   Prozessor %.2f ms   Budget %.1f ms", fps, gpuMs, cpuMs, quality.budgetMs()),
                passTimes(),
                (c == null ? "!Compute-Test läuft …" : (c.ok() ? "" : "!") + c.line()),
                String.format(Locale.GERMAN, "%s · Sonne %.1f° / %03.0f° · Belichtung %.3g", clock.label(), sp[0], sp[1], exposure()),
                wetter.describe(showClouds, cloudShade)[0],
                wetter.describe(showClouds, cloudShade)[1],
                wiring.zeile(),
                meer.describe(),
                brandung.meldung,
                bauwerke.describe(),
                Gelaende.describe(),
                cameraLine(),
                String.format(Locale.GERMAN, "%s · %d×%d · %s", quality.label, hdrW, hdrH, reversedZ ? "umgekehrte Tiefe" : "klassische Tiefe"),
                glInfo};
    }

    // ------------------------------------------------------------------ Bilder

    private void takePhoto(GL4 gl, int w, int h) {
        try {
            BufferedImage img = readDefault(gl, w, h);
            File dir = photoDir != null ? photoDir : new File("docs/status");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("Ordner " + dir + " lässt sich nicht anlegen");
            String name = photoName != null ? photoName : "Thunder_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            photoName = null;
            File f = new File(dir, name + ".png");
            javax.imageio.ImageIO.write(img, "png", f);
            photoMsg = "Foto gespeichert: " + f.getPath();
        } catch (Exception e) {
            photoMsg = "Foto fehlgeschlagen: " + e.getMessage();
        }
        System.out.println(photoMsg);
        photoMsgUntil = System.currentTimeMillis() + 5000;
    }

    BufferedImage readDefault(GL4 gl, int w, int h) {
        ByteBuffer bb = Buffers.newDirectByteBuffer(w * h * 4);
        gl.glBindFramebuffer(GL.GL_READ_FRAMEBUFFER, defaultFbo);
        gl.glReadBuffer(defaultFbo == 0 ? GL.GL_BACK : GL.GL_COLOR_ATTACHMENT0);
        gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);
        gl.glReadPixels(0, 0, w, h, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            int o = (h - 1 - y) * w * 4;
            for (int x = 0; x < w; x++) {
                int i = o + x * 4;
                row[x] = ((bb.get(i) & 255) << 16) | ((bb.get(i + 1) & 255) << 8) | (bb.get(i + 2) & 255);
            }
            img.setRGB(0, y, w, 1, row, 0, w);
        }
        return img;
    }

    public BufferedImage grab(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        defaultFbo = d.getContext().getDefaultDrawFramebuffer();
        return readDefault(gl, d.getSurfaceWidth(), d.getSurfaceHeight());
    }

    private void fail(String msg) {
        error = msg;
        System.err.println(msg);
        errorListener.accept(msg);
    }

    @Override
    public void dispose(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        for (Shader s : all) s.dispose(gl);
        hud.dispose(gl);
        wetter.dispose(gl);
        meer.dispose(gl);
        brandungGl.dispose(gl);
        bautenGl.dispose(gl);
        gelaendeGl.dispose(gl);
        belichtung.dispose(gl);
        Gpu.deleteFbos(gl, hdrFbo, hdrColorFbo);
        Gpu.deleteTextures(gl, hdrTex, depthTex);
        if (emptyVao != 0) gl.glDeleteVertexArrays(1, new int[]{emptyVao}, 0);
        for (int[] s : stamp) if (s[0] != 0) gl.glDeleteQueries(MARKS, s, 0);
        ready = false;
    }
}
