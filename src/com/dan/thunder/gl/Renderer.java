package com.dan.thunder.gl;

import com.dan.cloud.Clouds;
import com.dan.cloud.gl.CloudGl;
import com.dan.lightning.Bolt;
import com.dan.lightning.Lightning;
import com.dan.lightning.gl.LightningGl;
import com.dan.rain.gl.RainGl;
import com.dan.sky.Sky;
import com.dan.sky.gl.SkyGl;
import com.dan.thunder.camera.Ansichten;
import com.dan.thunder.camera.Camera;
import com.dan.thunder.model.Mood;
import com.dan.thunder.model.PaletteRole;
import com.dan.thunder.model.StormLevel;
import com.dan.thunder.model.ThunderData;
import com.dan.thunder.storm.StrikeKind;
import com.dan.thunder.scene.Bauwerke;
import com.dan.thunder.scene.Brandung;
import com.dan.thunder.scene.Gelaende;
import com.dan.thunder.scene.Gewitter;
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
 * <li><b>Wolken</b>: {@code com.dan.cloud} (Cumulonimbus) nach der Szene mit ihrer Tiefe; seit 18b leuchten sie
 * von innen: die Lichtpunkte der Blitze und der Glutkern ({@link Gewitter}).</li>
 * <li><b>Blitz und Regen</b> (seit 18b): die Kanäle aus {@code com.dan.lightning} (die Szene verdeckt sie), der Regen
 * aus {@code com.dan.rain} (Vorhänge, Streifen, Krönchen), das Leuchten der Luft um die Kanäle. Gelände, Bauten und
 * See bekommen das Licht der Blitze ({@code lightning.glsl}, Punktlichter der See), die Belichtung gibt im Blitz nach.</li>
 * <li><b>Nachbearbeitung</b>: Belichtung aus der Augenanpassung, Filmkurve; das HUD.</li>
 * </ol>
 * Der Klang ({@link Klang}, {@code com.dan.soundscape}) läuft neben dem Bild. Bedienung und Datenbank folgen in 18c.
 */
public final class Renderer implements GLEventListener {
    public static final int MIN_MAJOR = 4, MIN_MINOR = 3;
    public static final String VERSION = "Phase 18c";
    public static final String[] PASSES = {"Himmel", "Gelände", "Bauten", "Meer", "Wolken", "Blitz und Regen", "Nachbearbeitung", "HUD"};
    private static final int MARKS = PASSES.length + 1;
    static final double NEAR = 0.2, FAR = 60_000, FOG_DISTANCE = 30_000;

    public final SceneClock clock;
    public final Camera camera = new Camera();
    public final Wiring wiring;
    public final Wetter wetter = new Wetter();
    public final Meer meer = new Meer();
    public final Brandung brandung = new Brandung();
    /** Leuchtturm, Seebrücke, Strandkörbe; seit 18c mit den Körben aus TH_SCENE_OBJECT (neu gebaut, wenn die Daten kommen). */
    public volatile Bauwerke bauwerke = new Bauwerke();
    private volatile boolean bautenNeu;
    final BrandungGl brandungGl = new BrandungGl();
    final GelaendeGl gelaendeGl = new GelaendeGl();
    final BautenGl bautenGl = new BautenGl();
    private final Belichtung belichtung = new Belichtung();
    /** Das Gewitter (Regie, Blitze, Glutkern, Regen) und sein Klang (seit 18b). */
    public final Gewitter gewitter = new Gewitter();
    public final Klang klang;
    final LightningGl blitzGl = new LightningGl();
    /** Das Drehfeuer des Leuchtturms (seit Sylt Phase 19, beacon) und seine Grafik. */
    public final com.dan.thunder.scene.Leuchtfeuer feuer;
    final com.dan.beacon.gl.BeaconGl feuerGl = new com.dan.beacon.gl.BeaconGl();
    boolean feuerOk;
    private double umgebungLux = 1;
    final RainGl regenGl = new RainGl();
    boolean blitzOk, regenOk;
    String blitzCheck = "", regenCheck = "";
    /**
     * Lichteinheit der Blitze: {@code com.dan.lightning} rechnet in cd, {@code com.dan.sky} in Einheiten von
     * {@link Sky#CD_PER_UNIT} cd/m². Mit dieser Umrechnung steht ein Blitz in 5 km mit 0,4 lx im selben Maß wie die Nacht.
     */
    public static final double BLITZ_EINHEIT = 1 / Sky.CD_PER_UNIT;
    /** Wie weit die Belichtung im Blitz nachgibt (1 = so viel, dass ein voller Blitz gerade weiß wird) und wie schnell sie zurückgeht (s). */
    public static final double GIVE_WEISS = 1.5, GIVE_TAU = 0.25, WOLKE_GIBT = 0.3;
    private double flashGive = 1, nass;
    private int depthCopyTex;
    /** Leuchtdichte der Laternen (cd/m²): Linse und Glas des Leuchtturms, die Lampe der Seebrücke [G]. */
    public static final double TURM_LINSE = 4.0e4, TURM_GLAS = 600, BRUECKE_LINSE = 6.0e3, BRUECKE_GLAS = 900;
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
        klang = new Klang(this);
        feuer = new com.dan.thunder.scene.Leuchtfeuer(bauwerke.laterneTurm, com.dan.beacon.Beacon.Quality.MEDIUM);
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
        gewitter.setLevel(l);
        hudMeldung("Stufe " + (l == null ? "?" : l.name()));
    }

    /** Farben (TH_PALETTE) und Ort des Glutkerns (TH_SCENE) aus den Daten. */
    public void setDaten(ThunderData d) {
        gewitter.setDaten(d.palette(Mood.ABEND), d.palette(Mood.NACHT), d.scene(), wiring.mood);
        if (d.scene() == null) return;
        // seit 18c: Ansichten aus TH_VIEW, Strandkörbe aus TH_SCENE_OBJECT (Ort, Öffnung, Farbe)
        Ansichten.setzen(d.scene().views());
        java.util.List<com.dan.thunder.scene.Lage.Korb> k = new java.util.ArrayList<>();
        java.util.List<String> abweichung = new java.util.ArrayList<>();
        for (com.dan.thunder.model.SceneObject o : d.scene().objects()) {
            if (!o.hatLage()) continue;
            switch (o.type()) {
                case STRANDKORB -> k.add(new com.dan.thunder.scene.Lage.Korb(o.xM(), o.zM(), o.rotDeg() == null ? 225 : o.rotDeg(),
                        o.color() == null ? null : String.format("#%06X", o.color().getRGB() & 0xFFFFFF)));
                case LEUCHTTURM -> { var t = com.dan.thunder.scene.Lage.leuchtturm(); if (Math.hypot(o.xM() - t.xM(), o.zM() - t.zM()) > 1) abweichung.add("Leuchtturm"); }
                case SEEBRUECKE -> { var t = com.dan.thunder.scene.Lage.seebruecke(); if (Math.hypot(o.xM() - t.xM(), o.zM() - t.zM()) > 1) abweichung.add("Seebrücke"); }
                default -> { }
            }
        }
        if (!k.isEmpty() && !k.equals(bauwerke.koerbe)) {
            bauwerke = new Bauwerke(k);
            bautenNeu = true;
        }
        if (!abweichung.isEmpty()) hudMeldung("Hinweis: " + String.join(" und ", abweichung)
                + " stehen in TH_SCENE_OBJECT anders als im Code; sie hängen am Gelände und bleiben, wo der Code sie hat");
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
        if (wetter.skyOk && adaptLum > 0) return Sky.State.exposureFor(adaptLum, adaptation) * Math.pow(2, exposureBias + stimmungEv()) / flashGive;
        double b = Sun.skyBrightness(clock.sun()[0]);
        return 0.6 * Math.pow(b, -0.85) * Math.pow(2, exposureBias + stimmungEv()) / flashGive;
    }

    /** Belichtung ohne das Nachgeben im Blitz. */
    double exposureBase() { return exposure() * flashGive; }

    /** Licht am Turm in lx (für den Dämmerungsschalter), unter den Wolken gedämpft. */
    double lichtAmTurm() {
        if (!wetter.skyOk) return 1;
        Sky.State st = wetter.state();
        return com.dan.thunder.scene.Leuchtfeuer.lux(st.sunColor, st.sunDir[1], st.skyIrradiance) * (wetter.cloudsOk && showClouds ? 0.5 + 0.5 * cloudShade : 1);
    }

    public void readapt() {
        feuer.sofort(lichtAmTurm());
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
        try {
            feuerGl.init(gl);
            feuerOk = true;
        } catch (RuntimeException e) {
            System.err.println("Thunder: Leuchtfeuer nicht verfügbar: " + e.getMessage());
        }
        try {
            blitzGl.init(gl);
            blitzOk = true;
            System.out.println("Thunder: Blitze (lightning) bereit");
        } catch (RuntimeException e) {
            System.err.println("Thunder: Blitze nicht verfügbar: " + e.getMessage());
            errorListener.accept("Die Blitze (com.dan.lightning) lassen sich nicht starten.\n" + e.getMessage());
        }
        try {
            regenGl.init(gl);
            regenOk = true;
            System.out.println("Thunder: Regen " + regenGl.info());
        } catch (RuntimeException e) {
            System.err.println("Thunder: Regen nicht verfügbar: " + e.getMessage());
            errorListener.accept("Der Regen (com.dan.rain) lässt sich nicht starten.\n" + e.getMessage());
        }
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
        wetter.clouds.precipitation = gewitter.niederschlag();
        wiring.feed(meer.ocean);
        meer.ocean.step(dt);
        brandung.an = showBrandung && showOcean;
        if (brandung.see == null) brandung.see = (x, z) -> meer.ocean.heightAt(x, z);
        brandung.schritt(dt, wiring.seaHs(), wiring.seaTp());
        stepGewitter(dt, cloudsShown);
        klang.feed(dt);
        bebenT += dt;
        beben *= Math.exp(-dt / 0.25);
        if (beben < 0.01) beben = 0;
        if (wetter.skyOk) {
            Sky.State st = wetter.sky.step(dt);
            wetter.clouds.step(dt);
            cloudShade = cloudsShown ? Clouds.shadow(wetter.clouds.snapshot(), camera.eye[0], camera.eye[2], Wetter.keyDir(st)) : 1;
            adapt(st, dt);
        }
    }

    /** Der Blick, beim Beben ein wenig verwackelt (zwei Frequenzen je Achse). */
    private float[] bebenderBlick() {
        double b = beben;
        if (b <= 0 || frozen) return camera.view();
        double a = Math.toRadians(BEBEN_GRAD) * b, t = bebenT;
        double dy = a * (Math.sin(t * 61) * 0.6 + Math.sin(t * 37 + 1) * 0.4), dp = a * (Math.sin(t * 53 + 2) * 0.6 + Math.sin(t * 29) * 0.4);
        double[] f = camera.fwd, e = camera.eye;
        double[] r = {-f[2], 0, f[0]};
        double rl = Math.max(1e-6, Math.hypot(r[0], r[2]));
        return Mat4.lookAt(e[0], e[1] + dp * 0.02, e[2], e[0] + f[0] + r[0] / rl * dy, e[1] + f[1] + dp, e[2] + f[2] + r[2] / rl * dy);
    }

    /** Gewitter, Regen und Nässe weiter; wie weit die Belichtung im Blitz nachgibt. */
    private void stepGewitter(double dt, boolean cloudsShown) {
        gewitter.setStimmung(smooth(-4, -10, clock.sun()[0]));
        if (adaptLum > 0) gewitter.glutBezug = adaptLum * Sky.CD_PER_UNIT;
        gewitter.schritt(dt, wetter.clouds.snapshot(), cloudsShown, camera.eye.clone(), wiring.windSpeed(), wiring.windFromDeg());
        double r = gewitter.regen.rate > 0 ? gewitter.regen.rateAt(camera.eye[0], camera.eye[2]) : 0;
        nass += (Math.min(1, r / 8) - nass) * Math.min(1, dt / (r > 0 ? 40 : 300));
        // das Leuchtfeuer: Licht am Turm, Sicht, Regen am Turm
        umgebungLux = lichtAmTurm();
        double[] t = bauwerke.laterneTurm;
        feuer.schritt(dt, umgebungLux, wiring.visibilityKm(), gewitter.regen.rate > 0 ? gewitter.regen.rateAt(t[0], t[2]) : 0);
        geben(dt);
    }

    /** Die Belichtung gibt im Blitz nach (aus Lighthouse Phase 10, hier in Einheiten der Szene). */
    void geben(double dt) {
        double[] e = camera.eye;
        Lightning.Snapshot s = gewitter.blitz.snapshot();
        double lf = 0;
        if (gewitter.blitzeAn) {
            // seit Sylt Phase 19: die hellen Stellen der Wolkenunterseite zählen nur zu 30 % (sonst gab die Belichtung bei der
            // Krone so weit nach, dass ihre Äste kaum zu sehen waren; Befund 18c); die Fläche am Auge zählt voll
            double peak = s.illuminanceAt(e[0], e[1], e[2], 0) * 0.6 / Math.PI;
            lf = Math.max(peak, WOLKE_GIBT * s.flashLuminance(e[0], e[1], e[2], gewitter.blitz.cloudBase)) * BLITZ_EINHEIT;
            // die Luft dämpft das Licht bis zum Auge (das Paket rechnet hier ohne): Weg zum hellsten Lichtpunkt
            java.util.List<Lightning.Light> l = s.lights(1, 1);
            if (!l.isEmpty()) {
                Lightning.Light h = l.get(0);
                double d = Math.sqrt((h.x() - e[0]) * (h.x() - e[0]) + (h.y() - e[1]) * (h.y() - e[1]) + (h.z() - e[2]) * (h.z() - e[2]));
                lf *= Math.exp(-luft(wetter.skyOk ? wetter.state() : null)[0] * d);
            }
        }
        double give = 1 + exposureBase() * lf / GIVE_WEISS;
        flashGive = frozen || dt <= 0 ? give : Math.max(give, flashGive * Math.exp(-Math.max(0, dt) / GIVE_TAU));
        lfZuletzt = lf;
    }

    private double lfZuletzt;

    /** Für die Prüfung: Belichtung, Nachgeben, Leuchtdichte des Blitzes (Einheiten), Lichtpunkte. */
    public String blitzDiagnose() {
        return String.format(Locale.ROOT, "pre %.3g give %.3g lf %.3g lights %d cloud %d glut %.3g", exposureBase(), flashGive, lfZuletzt,
                blitzGl.lightCount(), blitzGl.cloudLightCount(), gewitter.glutRgb[1]);
    }

    // ------------------------------------------------------------------ Bedienung (seit 18c)

    /** Das Bild bebt, wenn der Donner eines nahen Blitzes ankommt (wie im 2D-Thunder): Stärke 0..1, klingt in 0,7 s ab. */
    private volatile double beben;
    private double bebenT;
    public static final double BEBEN_GRAD = 0.35, BEBEN_ABSTAND = 1500;

    /** Vom Klang: ein Donner ist angekommen (Abstand des nächsten Kanalpunkts in m). */
    void donnerAngekommen(double abstand) {
        if (abstand < BEBEN_ABSTAND) beben = Math.max(beben, 1 - abstand / BEBEN_ABSTAND);
    }

    /**
     * Klick in die Szene (Fensterpunkte x, y in w × h): ein Blitz über die Regie. Trifft der Strahl See oder Strand, schlägt
     * er dort ein; im Himmel gilt wie im 2D-Thunder: je höher der Klick, desto näher (0,4 bis 9,4 km). Gibt die Peilung
     * und den Abstand zurück.
     */
    public double[] klick(int x, int y, int w, int h) {
        double t = Math.tan(Math.toRadians(fovy() / 2)), asp = w / (double) Math.max(1, h);
        double nx = 2.0 * x / Math.max(1, w) - 1, ny = 1 - 2.0 * y / Math.max(1, h);
        double[] f = camera.fwd.clone();
        double[] r = {-f[2], 0, f[0]};
        double rl = Math.hypot(r[0], r[2]);
        if (rl < 1e-6) r = new double[]{1, 0, 0}; else { r[0] /= rl; r[2] /= rl; }
        double[] u = {r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0]};
        double[] d = new double[3];
        for (int k = 0; k < 3; k++) d[k] = f[k] + r[k] * nx * t * asp + u[k] * ny * t;
        double peil = com.dan.thunder.scene.Lage.kurs(0, 0, d[0], d[2]);
        double km;
        double[] e = camera.eye;
        if (d[1] < -1e-4) {
            double s = -e[1] / d[1];
            double px = e[0] + d[0] * s, pz = e[2] + d[2] * s;
            peil = com.dan.thunder.scene.Lage.kurs(0, 0, px, pz);
            km = Math.hypot(px, pz) / 1000;
        } else {
            double hoch = Math.toDegrees(Math.atan2(d[1], Math.hypot(d[0], d[2])));
            double q = 1 - Math.min(1, hoch / 25);
            km = 0.4 + q * q * 9;
        }
        gewitter.vonHand(StrikeKind.CG, peil, km);
        return new double[]{peil, km};
    }

    /** Tasten: B Erdblitz in Blickrichtung (1,5 bis 4 km), K Blitzkrone, T Einschlag in den Leuchtturm. */
    public void blitzTaste(char c) {
        switch (c) {
            case 'B' -> gewitter.vonHand(StrikeKind.CG, camera.heading() + (Math.random() - 0.5) * 20, 1.5 + Math.random() * 2.5);
            case 'K' -> gewitter.vonHand(StrikeKind.CROWN, Site.BLICK, 5);
            case 'T' -> { double[] t = Gewitter.turm(); gewitter.vonHand(StrikeKind.CG, t[0], t[1]); }
            default -> { }
        }
    }

    /** Qualität eine Stufe weiter (Taste G). */
    public Quality naechsteQualitaet() {
        Quality[] q = Quality.values();
        Quality n = q[(quality.ordinal() + 1) % q.length];
        setQuality(n);
        hudMeldung("Qualität " + n.summary());
        return n;
    }

    private final HudOverlay karte = new HudOverlay();

    /** Die Blitzkarte oben rechts (wie im 2D-Thunder): letzter Blitz, Donner, Blitze je Minute. */
    BufferedImage paintKarte() {
        String z = gewitter.zuletzt;
        Lightning.Thunder next = null;
        for (Lightning.Thunder th : gewitter.blitz.pendingThunder()) if (next == null || th.time() < next.time()) next = th;
        Lightning.Thunder last = gewitter.blitz.lastThunder();
        String donner = next != null ? String.format(Locale.GERMAN, "Donner in %.1f s", Math.max(0, next.time() - gewitter.blitz.time()))
                : last != null && gewitter.blitz.time() - last.time() < last.duration() ? String.format(Locale.GERMAN, "es donnert · %.0f dB", last.levelDb())
                : "still";
        StormLevel l = wiring.level;
        String[] lines = {"LETZTER BLITZ", z.isEmpty() ? "noch keiner" : z, donner,
                String.format(Locale.GERMAN, "%d Blitze je Minute · %s%s", gewitter.regie().perMinute(gewitter.time() * 1000), l == null ? "–" : l.name(), gewitter.sanft() ? " · sanft" : "")};
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 13), head = new Font(Font.SANS_SERIF, Font.BOLD, 11);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        int tw = 0;
        for (int i = 1; i < lines.length; i++) tw = Math.max(tw, pg.getFontMetrics(font).stringWidth(lines[i]));
        pg.dispose();
        int w = Math.max(230, tw + 26), h = 22 + 19 * (lines.length - 1) + 10;
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(10, 14, 24, 190));
        g.fillRoundRect(0, 0, w - 1, h - 1, 12, 12);
        g.setColor(new Color(120, 150, 255, 225));
        g.setStroke(new BasicStroke(2f));
        g.drawLine(10, 1, w - 40, 1);
        g.setColor(new Color(255, 154, 80, 225));
        g.drawLine(w - 38, 1, w - 12, 1);
        g.setFont(head);
        g.setColor(new Color(160, 180, 230));
        g.drawString(lines[0], 12, 17);
        g.setFont(font);
        for (int i = 1; i < lines.length; i++) {
            g.setColor(i == 1 ? new Color(236, 238, 244) : i == 2 && next != null ? new Color(255, 190, 120) : new Color(186, 192, 204));
            g.drawString(lines[i], 12, 18 + 19 * i);
        }
        g.dispose();
        return im;
    }

    /**
     * Für Standbilder: alle Blitze weg, dann einer von Hand (Art, Peilung, km) und das Gewitter bis zum Schlag
     * {@code schlag} plus {@code nach} s weiter; die Krone kommt ganz. Die Regie bleibt aus.
     */
    public Bolt blitzStandbild(StrikeKind k, double peilung, double km, double u, int schlag, double nach) {
        gewitter.regieAn = false;
        gewitter.leeren();
        Bolt b = gewitter.ausloesen(k, peilung, km, u);
        if (k == StrikeKind.CROWN) gewitter.kroneSofort();
        if (b != null) {
            double t = b.strokeTime[Math.min(schlag, b.strokeTime.length - 1)] + nach;
            gewitter.blitz.step(Math.max(1e-3, t));
            gewitter.blitz.updateVisibility();
        }
        blitzGl.invalidate();
        geben(0);
        return b;
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
        if (bautenNeu) {
            bautenNeu = false;
            bautenGl.upload(gl, BautenGl.FEST, bauwerke.netz);
        }
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
        float[] vp = Mat4.mul(proj, bebenderBlick());
        float[] inv = Mat4.invert(vp);
        boolean cloudsNow = frameSky && wetter.cloudsOk && showClouds;
        Clouds.Snapshot cs = wetter.clouds.snapshot();
        CloudGl.Light cl = null;
        // seit 18b: die Blitze übernehmen (neue Kanäle hochladen, Lichtpunkte für Wolken, See, Gelände und Bauten)
        Lightning.Snapshot ls = gewitter.blitz.snapshot();
        if (blitzOk) blitzGl.update(gl, ls);
        boolean blitzJetzt = blitzOk && gewitter.blitzeAn && blitzGl.active();
        double preU = (frameSky ? pre : exposure()) * BLITZ_EINHEIT;
        double[] luft = luft(st);
        double decke = cloudsNow ? cs.type().base : gewitter.blitz.cloudBase;

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
                inDieWolken(cl, preU, blitzJetzt);
                wetter.cloudGl.update(gl, cs, cl, camera.eye.clone());
                if (wetter.cloudCheck.isEmpty() && frame > 40) wetter.cloudCheck = wetter.cloudGl.selfCheck(gl, cs, cl);
            }
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, wetter.panoFbo);
            gl.glViewport(0, 0, Wetter.PANO_W, Wetter.PANO_H);
            wetter.skyGl.drawPanorama(gl, st, pre);
            if (cloudsNow) wetter.cloudGl.drawPanorama(gl, cs, cl, camera.eye.clone());
            // seit 18b: Kanäle und Leuchten ins Rundumbild, damit die See sie spiegelt
            if (blitzJetzt) blitzGl.drawPanorama(gl, Wetter.PANO_W, Wetter.PANO_H, camera.eye.clone(), preU, luft[0], luft[1], decke);
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
        // Regen: Tropfen und Ringe weiter; die Selbsttests einmal
        boolean regenJetzt = regenOk && gewitter.regenAn && gewitter.regen.rate > 0;
        if (regenOk) {
            regenGl.update(gl, gewitter.regen.snapshot());
            if (regenCheck.isEmpty() && regenJetzt && frame > 60) regenCheck = regenGl.selfCheck(gl, gewitter.regen.snapshot());
        }
        if (blitzJetzt && blitzCheck.isEmpty() && !frozen) {
            for (Bolt b : ls.bolts())
                if (b.kind == Bolt.Kind.CLOUD_GROUND && ls.time() > b.t0 + b.leaderTime) {
                    blitzCheck = blitzGl.selfCheck(gl, ls);
                    blitzGl.update(gl, ls);
                    break;
                }
        }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        gl.glBindVertexArray(emptyVao);
        mark(gl, 1);

        // 2 Gelände
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(true);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        common(gl, gelaendeShader, vp, inv, sun);
        cloudShadow(gl, gelaendeShader, cloudsNow);
        blitzBinden(gl, gelaendeShader, preU, luft[0], blitzJetzt);
        gelaendeShader.set(gl, "uWet", (float) nass);
        gelaendeGl.draw(gl);
        gl.glBindVertexArray(emptyVao);
        mark(gl, 2);

        // 3 Bauten
        if (showBauten && bautenGl.ok) {
            common(gl, bautenShader, vp, inv, sun);
            cloudShadow(gl, bautenShader, cloudsNow);
            blitzBinden(gl, bautenShader, preU, luft[0], blitzJetzt);
            bautenShader.set(gl, "uNight", (float) smooth(-1, -6, sp[0]));
            double k = (frameSky ? pre : exposure()) / Sky.CD_PER_UNIT;
            bautenShader.set(gl, "uWarm", (float) (15 * k), (float) (15 * k * 0.72), (float) (15 * k * 0.42));
            // Laternen (seit 18b): Leuchtturm (K) und Seebrücke (Q) brennen im Gewitter, Farbe LAMP aus TH_PALETTE
            double[] lt = bauwerke.laterneTurm, lb = bauwerke.laterneBruecke;
            bautenShader.set(gl, "uTurmK", (float) lt[0], (float) lt[2], (float) lt[1], 0.3f);
            bautenShader.set(gl, "uTurmQ", (float) lb[0], (float) lb[2], (float) lb[1], 0.1f);
            double[] lamp = gewitter.farbe(PaletteRole.LAMP);
            double ku = preU;
            // seit Sylt Phase 19: die Laterne des Turms nach dem Drehfeuer (Linse zum Auge, Glas), wie in Sylt
            if (feuerOk && feuer.an) {
                feuerGl.update(gl, feuer.feuer.snapshot(), feuer.grid);
                gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
                gl.glViewport(0, 0, w, h);
                gl.glEnable(GL.GL_DEPTH_TEST);
                gl.glDepthMask(true);
                gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
                gl.glDisable(GL.GL_BLEND);
                gl.glBindVertexArray(emptyVao);
                bautenShader.use(gl);
                laterne(gl, feuer.feuer.snapshot(), ku);
            } else {
                bautenShader.set(gl, "uLensK", Wetter.scale(lamp, TURM_LINSE * ku));
                bautenShader.set(gl, "uGlowK", Wetter.scale(lamp, TURM_GLAS * ku));
            }
            bautenShader.set(gl, "uLensQ", Wetter.scale(lamp, BRUECKE_LINSE * ku));
            bautenShader.set(gl, "uGlowQ", Wetter.scale(lamp, BRUECKE_GLAS * ku));
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
                    ol -> {
                        if (netz) brandungGl.feed(ol, brandung);
                        aufsMeer(ol, preU, luft, blitzJetzt);
                        if (regenJetzt) {
                            ol.detailTexture = regenGl.rippleTexture();
                            ol.detailTile = regenGl.rippleTile();
                            ol.detailStrength = regenGl.rippleStrength();
                            ol.detailRange = 60;
                        }
                    });
            gl.glBindVertexArray(emptyVao);
            if (netz) {
                Brandung.Stueck bs = brandung.stueck();
                double[] m = bs.welt(bs.xWasserlinie, Brandung.BREITE / 2);
                double[] bl = blitzJetzt ? Wetter.scale(bestrahlung(m[0], 1, m[1], preU, luft[0]), 1 / Math.PI) : null;
                brandungGl.draw(gl, vp, camera.eye, reversedZ, brandung, st, pre, sun, wetter, showClouds, sunLight(sp[0]), FOG_DISTANCE, clock.t(), camera.heading(), bl);
            }
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

        // 6 Blitz und Regen (seit 18b): Tiefe kopieren; die Kanäle mit Tiefentest (die Szene verdeckt sie), der Regen,
        // dann das Leuchten der Luft um die Kanäle bis zur Szene
        if (blitzJetzt || regenJetzt) {
            gl.glCopyImageSubData(depthTex, GL.GL_TEXTURE_2D, 0, 0, 0, 0, depthCopyTex, GL.GL_TEXTURE_2D, 0, 0, 0, 0, w, h, 1);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
        }
        LightningGl.View lv = blitzJetzt ? blitzView(vp, inv, w, h, preU, luft, decke) : null;
        if (blitzJetzt) {
            gl.glEnable(GL.GL_DEPTH_TEST);
            lv.depthTest = true;
            blitzGl.drawChannels(gl, lv);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
            gl.glBindVertexArray(emptyVao);
        }
        if (regenJetzt) {
            zeichneRegen(gl, st, vp, inv, w, h, pre, preU, luft[0], blitzJetzt);
            gl.glBindVertexArray(emptyVao);
        }
        // seit Sylt Phase 19: der Strahl des Leuchtfeuers durch Dunst und Regen, dazu sein Lichtpunkt
        if (feuerOk && feuer.an) {
            com.dan.beacon.Beacon.Snapshot fs = feuer.feuer.snapshot();
            com.dan.beacon.gl.BeaconGl.View fv = new com.dan.beacon.gl.BeaconGl.View();
            fv.viewProj = vp;
            fv.invViewProj = inv;
            fv.eye = camera.eye.clone();
            fv.reversedZ = reversedZ;
            fv.width = w;
            fv.height = h;
            fv.fovyDeg = fovy();
            fv.depthTexture = depthTex;
            fv.targetFbo = hdrColorFbo;
            fv.preExposure = preU;
            fv.maxDistance = 40_000;
            fv.jitter = !frozen;
            gl.glDisable(GL.GL_DEPTH_TEST);
            if (feuer.strahl && fs.lit()) feuerGl.drawBeam(gl, fv); else feuerGl.beamOff();
            if (fs.lit()) feuerGl.drawGlare(gl, fv, java.util.List.of(glare(fs, camera.eye, fovy(), h, feuer.feuer.hazePerM)));
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
            gl.glBindVertexArray(emptyVao);
        }
        if (blitzJetzt) {
            gl.glDisable(GL.GL_DEPTH_TEST);
            lv.depthTest = false;
            lv.depthTexture = depthCopyTex;
            blitzGl.drawGlow(gl, lv);
            gl.glBindVertexArray(emptyVao);
        }
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
        mark(gl, 6);

        // 7 Nachbearbeitung
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
        mark(gl, 7);

        // 8 HUD
        gl.glFlush();
        if (showHud) {
            hud.refresh(gl, 250, this::paintHud, frozen);
            hud.draw(gl, overlay, emptyVao, 12, Math.max(0, surfH - hud.height - 12), surfW, surfH);
            // seit 18c: die Blitzkarte oben rechts
            karte.refresh(gl, 150, this::paintKarte, frozen);
            karte.draw(gl, overlay, emptyVao, Math.max(0, surfW - karte.width - 12), Math.max(0, surfH - karte.height - 12), surfW, surfH);
        }
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        mark(gl, 8);
    }

    /** Die Laterne des Turms aus dem Zustand des Feuers (aus Sylt, {@code Renderer.laterne}). */
    private void laterne(GL4 gl, com.dan.beacon.Beacon.Snapshot s, double k) {
        double half = s.lensHalfHeight();
        bautenShader.set(gl, "uTurmK", (float) s.x(), (float) s.z(), (float) s.y(), (float) half);
        if (!s.lit()) {
            bautenShader.set(gl, "uLensK", 0f, 0f, 0f);
            bautenShader.set(gl, "uGlowK", 0f, 0f, 0f);
            return;
        }
        double dx = camera.eye[0] - s.x(), dy = camera.eye[1] - s.y(), dz = camera.eye[2] - s.z(), dl = Math.max(0.1, Math.sqrt(dx * dx + dy * dy + dz * dz));
        double g = s.lensGlow();
        double[] tint = s.tint(), L = s.lensLuminance(dx / dl, dy / dl, dz / dl);
        for (int c = 0; c < 3; c++) L[c] = Math.max(0, L[c] - g * tint[c]);
        double cap = 5e4;
        bautenShader.set(gl, "uLensK", (float) (Math.min(cap, L[0]) * k), (float) (Math.min(cap, L[1]) * k), (float) (Math.min(cap, L[2]) * k));
        bautenShader.set(gl, "uGlowK", (float) (g * tint[0] * k), (float) (g * tint[1] * k), (float) (g * tint[2] * k));
    }

    /** Lichtpunkt des Feuers am Auge (aus Sylt, {@code FeuerGl.glare}). */
    static com.dan.beacon.gl.BeaconGl.Glare glare(com.dan.beacon.Beacon.Snapshot s, double[] e, double fovy, int h, double sigma) {
        double[] lux = s.illuminanceAt(e[0], e[1], e[2], sigma);
        double dl = Math.sqrt((e[0] - s.x()) * (e[0] - s.x()) + (e[1] - s.y()) * (e[1] - s.y()) + (e[2] - s.z()) * (e[2] - s.z()));
        double px = 2 * Math.tan(Math.toRadians(fovy) / 2) / Math.max(1, h) * dl;
        return new com.dan.beacon.gl.BeaconGl.Glare(s.x(), s.y(), s.z(), lux, Math.max(0, Math.min(1, (2 * px - 0.9) / 0.9)), 2.5);
    }

    // ------------------------------------------------------------------ Gewitter auf der Grafikkarte (seit 18b)

    /** Auslöschung und Streuung der Luft für das Licht der Blitze (Dunst aus der Sicht, Regen am Auge), 1/m. */
    double[] luft(Sky.State st) {
        // die Sicht der Stufe meint den Regen mit; für das Licht der Blitze zählt der Dunst höchstens wie bei 20 km Sicht
        double haze = st != null ? 3.912 / (Math.max(20, st.visibilityKm) * 1000) : 1e-4;
        double[] e = camera.eye;
        // der Regen steht in Schauern: auf dem Weg zu einem fernen Blitz im Mittel nur auf einem Teil (0,15) [G]
        double r = regenOk && gewitter.regen.rate > 0 ? gewitter.regen.scatter(e[0], e[1], e[2]) * 3e-3 * 0.15 : 0;
        // das Leuchten um die Kanäle: der Regen streut vor allem nach vorn, zum Auge hin weniger [G]
        // und nur ein Drittel des Dunsts leuchtet sichtbar um den Kanal (das Paket rechnet mit klarer Luft) [G]
        return new double[]{haze + r, haze * 0.3 + r * 0.2};
    }

    /** Farbe des Blitzlichts auf der Szene: BOLT_GLOW aus TH_PALETTE zur Hälfte (F48 A). */
    double[] blitzTon() {
        double[] f = gewitter.farbe(PaletteRole.BOLT_GLOW);
        return new double[]{0.5 + 0.5 * f[0], 0.5 + 0.5 * f[1], 0.5 + 0.5 * f[2]};
    }

    /** Uniforms von lightning.glsl (Lichtpunkte vorbelichtet, in Einheiten der Szene, getönt); ohne Blitz: keine Lichter. */
    private void blitzBinden(GL4 gl, Shader sh, double preU, double sigma, boolean an) {
        int n = an ? Math.min(8, blitzGl.lightCount()) : 0;
        float[] pos = new float[32], rgb = new float[32];
        double[] ton = blitzTon();
        for (int i = 0; i < n; i++) {
            double[] lp = blitzGl.lightPos(i), lr = blitzGl.lightRgb(i);
            for (int k = 0; k < 4; k++) pos[i * 4 + k] = (float) lp[k];
            for (int k = 0; k < 3; k++) rgb[i * 4 + k] = (float) (lr[k] * preU * ton[k]);
        }
        sh.set(gl, "ltgCount", n);
        gl.glUniform4fv(sh.loc(gl, "ltgPos"), 8, pos, 0);
        gl.glUniform4fv(sh.loc(gl, "ltgRgb"), 8, rgb, 0);
        sh.set(gl, "ltgSigma", (float) sigma);
    }

    /** Bestrahlung von oben am Ort (vorbelichtet, Einheiten der Szene) durch alle Lichtpunkte der Blitze. */
    double[] bestrahlung(double x, double y, double z, double preU, double sigma) {
        double[] e = new double[3], ton = blitzTon();
        int n = blitzGl.lightCount();
        for (int i = 0; i < n; i++) {
            double[] lp = blitzGl.lightPos(i), lr = blitzGl.lightRgb(i);
            double dx = lp[0] - x, dy = lp[1] - y, dz = lp[2] - z, d2 = dx * dx + dy * dy + dz * dz, d = Math.sqrt(d2 + 1);
            double f = Math.max(0, dy / d) * Math.exp(-sigma * d) / (d2 + lp[3] * lp[3]) * preU;
            for (int k = 0; k < 3; k++) e[k] += lr[k] * ton[k] * f;
        }
        return e;
    }

    /** Lichtpunkte in die Wolken: die der Blitze (höchstens drei) und der Glutkern (F50 A), vorbelichtet. */
    private void inDieWolken(CloudGl.Light cl, double preU, boolean blitz) {
        int n = 0;
        if (blitz) {
            int m = Math.min(2, blitzGl.cloudLightCount());
            for (int i = 0; i < m; i++, n++) {
                double[] c = blitzGl.cloudLight(i);
                for (int k = 0; k < 3; k++) {
                    cl.flashPos[n * 4 + k] = (float) c[k];
                    cl.flashRgb[n * 4 + k] = (float) (c[3] * Lightning.CHANNEL_RGB[k] * preU);
                }
            }
        }
        // der Glutkern als zwei Punkte quer zum Blick (±0,9 km): ein breites Glimmen statt eines Flecks
        double[] g = gewitter.glutOrt, gr = gewitter.glutRgb;
        if (gr[0] + gr[1] + gr[2] > 0) {
            for (int q = -1; q <= 1; q += 2) {
                cl.flashPos[n * 4] = (float) (g[0] + q * 900 * Site.RX);
                cl.flashPos[n * 4 + 1] = (float) (g[1] - 150 * q);
                cl.flashPos[n * 4 + 2] = (float) (g[2] + q * 900 * Site.RZ);
                for (int k = 0; k < 3; k++) cl.flashRgb[n * 4 + k] = (float) (gr[k] * preU * 0.6);
                n++;
            }
        }
        cl.flashCount = n;
    }

    /** Die Lichtpunkte der Blitze auf Wasser und Schaum (bis acht), getönt. */
    private void aufsMeer(com.dan.ocean.gl.OceanGl.Light ol, double preU, double[] luft, boolean an) {
        if (!an) return;
        int n = Math.min(8, blitzGl.lightCount());
        double[] ton = blitzTon();
        ol.flashCount = n;
        for (int i = 0; i < n; i++) {
            double[] lp = blitzGl.lightPos(i), lr = blitzGl.lightRgb(i);
            for (int k = 0; k < 4; k++) ol.flashPos[i * 4 + k] = (float) lp[k];
            for (int k = 0; k < 3; k++) ol.flashRgb[i * 4 + k] = (float) (lr[k] * preU * ton[k]);
        }
        if (ol.pointSigma <= 0) ol.pointSigma = luft[0];
    }

    private LightningGl.View blitzView(float[] vp, float[] inv, int w, int h, double preU, double[] luft, double decke) {
        LightningGl.View v = new LightningGl.View();
        v.viewProj = vp;
        v.invViewProj = inv;
        v.eye = camera.eye.clone();
        v.reversedZ = reversedZ;
        v.width = w;
        v.height = h;
        v.fovyDeg = fovy();
        v.targetFbo = hdrFbo;
        v.preExposure = preU;
        v.extinction = luft[0];
        v.scatter = luft[1];
        v.ceiling = decke;
        return v;
    }

    /**
     * Der Regen (aus Lighthouse Phase 7): Sonne, Mond und Himmel vorbelichtet, unter der Wolke gedämpft; im Blitz
     * nimmt das stärkste Blitzlicht den Platz des Monds ein.
     */
    private void zeichneRegen(GL4 gl, Sky.State st, float[] vp, float[] inv, int w, int h, double pre, double preU, double sigma, boolean blitz) {
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        RainGl.View v = new RainGl.View();
        v.viewProj = vp;
        v.invViewProj = inv;
        v.eye = camera.eye.clone();
        v.forward = camera.fwd.clone();
        v.reversedZ = reversedZ;
        v.near = NEAR;
        v.far = FAR;
        v.fovyDeg = fovy();
        v.width = w;
        v.height = h;
        v.depthTexture = depthCopyTex;
        RainGl.Light l = new RainGl.Light();
        if (st != null) {
            double under = wetter.cloudsOk && showClouds ? Math.min(1, wetter.clouds.type.overcastBrightness * 1.6) : 1;
            double a = Math.toRadians(camera.heading());
            l.sunDir = st.sunDir;
            l.sunColor = Wetter.scale(st.sunColor, pre * cloudShade);
            l.moonDir = st.moonDir;
            l.moonColor = Wetter.scale(st.moonColor, pre * cloudShade);
            l.skyTop = Wetter.scale(st.skyIrradiance, pre / Math.PI * under);
            l.skyHorizon = Wetter.scale(st.radiance(new double[]{Math.sin(a), 0.03, -Math.cos(a)}), pre);
            l.fogDistance = Wetter.fogDistance(st);
        }
        if (blitz) {
            double[] e = camera.eye, best = null, tot = new double[3];
            double bestL = 0;
            double[] ton = blitzTon();
            for (int i = 0; i < blitzGl.lightCount(); i++) {
                double[] p = blitzGl.lightPos(i), c = blitzGl.lightRgb(i);
                double dx = p[0] - e[0], dy = p[1] - e[1], dz = p[2] - e[2], d2 = dx * dx + dy * dy + dz * dz + p[3] * p[3], d = Math.sqrt(dx * dx + dy * dy + dz * dz + 1);
                double el = (0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]) / d2 * Math.exp(-sigma * d);
                for (int k = 0; k < 3; k++) tot[k] += c[k] * ton[k] / d2 * Math.exp(-sigma * d) * preU;
                if (el > bestL) { bestL = el; best = new double[]{dx / d, dy / d, dz / d}; }
            }
            double lm = 0.2126 * l.moonColor[0] + 0.7152 * l.moonColor[1] + 0.0722 * l.moonColor[2];
            if (best != null && 0.2126 * tot[0] + 0.7152 * tot[1] + 0.0722 * tot[2] > lm) {
                l.moonDir = best;
                l.moonColor = tot;
            }
        }
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        regenGl.draw(gl, v, l, 1);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
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
        Gpu.deleteTextures(gl, depthCopyTex);
        depthCopyTex = Gpu.texture(gl, w, h, GL4.GL_DEPTH_COMPONENT32F, false);
        gl.glBindTexture(GL.GL_TEXTURE_2D, depthCopyTex);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
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
        if (!gewitter.zuletzt.isEmpty()) l.add("Zuletzt " + gewitter.zuletzt);
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
                (blitzOk ? "" : "!") + gewitter.zeile() + (blitzCheck.isEmpty() ? "" : " · " + blitzCheck),
                (regenOk ? "" : "!") + gewitter.regenZeile() + (regenOk && gewitter.regen.rate > 0 ? " · " + regenGl.info() : "") + (regenCheck.isEmpty() ? "" : " · " + regenCheck)
                        + String.format(Locale.GERMAN, " · Sand nass %.0f %%", nass * 100),
                klang.describe(),
                (feuerOk ? "" : "!") + feuer.describe(),
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
        karte.dispose(gl);
        wetter.dispose(gl);
        meer.dispose(gl);
        brandungGl.dispose(gl);
        if (blitzOk) blitzGl.dispose(gl);
        if (feuerOk) feuerGl.dispose(gl);
        if (regenOk) regenGl.dispose(gl);
        klang.dispose();
        bautenGl.dispose(gl);
        gelaendeGl.dispose(gl);
        belichtung.dispose(gl);
        Gpu.deleteFbos(gl, hdrFbo, hdrColorFbo);
        Gpu.deleteTextures(gl, hdrTex, depthTex, depthCopyTex);
        if (emptyVao != 0) gl.glDeleteVertexArrays(1, new int[]{emptyVao}, 0);
        for (int[] s : stamp) if (s[0] != 0) gl.glDeleteQueries(MARKS, s, 0);
        ready = false;
    }
}
