package com.dan.thunder.app;

import com.dan.fframe.FFrame;
import com.dan.ficons.FIconType;
import com.dan.fstyle.FTheme;
import com.dan.fstyle.FThemePreset;
import com.dan.thunder.camera.Ansichten;
import com.dan.thunder.data.BuiltinThunderRepository;
import com.dan.thunder.data.DbConfig;
import com.dan.thunder.data.OracleThunderRepository;
import com.dan.thunder.gl.Renderer;
import com.dan.thunder.gl.SceneView;
import com.dan.thunder.model.Mood;
import com.dan.thunder.model.StormLevel;
import com.dan.thunder.model.ThunderData;
import com.dan.thunder.scene.SceneClock;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;

/**
 * Thunder – ein Sommergewitter über der Ostsee, seit Phase 18 in 3D auf {@code Renderer.jar} (Sylt-Werkbuch F30 A).
 * <p>
 * Start: {@code java -jar Thunder.jar} (liest Oracle, fällt bei Bedarf auf eingebaute Daten zurück),
 * {@code --offline} ohne Datenbank, {@code -Dthunder.gljpanel=true} zeichnet über ein GLJPanel (Rückfall), {@code --nacht} mit Nachtstimmung. Die Bedienleiste bleibt: Stufe, Abend und Nacht,
 * Regen, Ton, Sanfte Blitze, Theme. Tasten 1 bis 5: die Ansichten, Pos1 der Hauptblick, H das HUD, F12 ein Foto.
 * Seit 18c: ein Klick in die Szene ist ein Blitz (je höher im Himmel, desto näher; auf See und Strand genau dort),
 * B ein Erdblitz in Blickrichtung, K eine Blitzkrone, T ein Einschlag in den Leuchtturm, G die nächste Qualitätsstufe.
 * Mit Datenbank schreibt das Blitzprotokoll jeden Blitz nach {@code TH_STRIKE}, seit {@code sql/04_th_3d.sql} mit
 * seinem Ort in Metern. Das Fenster startet maximiert.
 * <p>
 * Stand Phase 18b: die Szene (Strand, Düne, Meer mit Brandung, Himmel, Wolken, Leuchtturm, Seebrücke, Strandkörbe)
 * mit dem Gewitter: die Regie des 2D-Thunder löst die Blitze aus ({@code lightning}), der Glutkern atmet in der Wolke,
 * es regnet ({@code rain}), der Klang kommt aus {@code soundscape}. Stufe und Stimmung wirken auf Wind, See, Wolken,
 * Blitze, Regen und Uhr. Das Blitzprotokoll und die Datenbank folgen in 18c.
 */
public final class ThunderApp implements ControlBar.Actions {

    private final FFrame frame = new FFrame("Thunder · Gewitter über der Ostsee");
    private final Renderer renderer;
    private final SceneView view;
    private final ControlBar bar;
    private final FTheme.ThemeListener themeListener = t -> applyFrameTheme();
    private DbConfig config;
    private volatile com.dan.thunder.data.StrikeLogger logger;
    private String sourceText = "";

    private ThunderApp(Mood mood) {
        renderer = new Renderer(new SceneClock());
        renderer.setMood(mood, true);
        view = new SceneView(renderer);
        bar = new ControlBar(this, mood);
    }

    public static void main(String[] args) {
        boolean offline = Arrays.asList(args).contains("--offline");
        Mood mood = Arrays.asList(args).contains("--nacht") ? Mood.NACHT : Mood.ABEND;
        SwingUtilities.invokeLater(() -> {
            FTheme.getInstance().applyPreset(FThemePreset.MIDNIGHT_BLUE);
            new ThunderApp(mood).start(offline);
        });
    }

    private void start(boolean offline) {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        if (!view.lightweight) deckend();
        frame.setLogoType(FIconType.BEACH);
        frame.getComponentPane().add(view, BorderLayout.CENTER);
        frame.getComponentPane().add(bar, BorderLayout.SOUTH);
        frame.setPreferredFrameSize(new Dimension(1440, 900));
        frame.setMinimumSize(new Dimension(1180, 720));
        applyFrameTheme();
        FTheme.getInstance().addThemeListener(themeListener);
        // die Ansichten zur Zeit des Tastendrucks (seit 18c aus TH_VIEW, sobald die Daten da sind)
        for (int k = 1; k <= 9; k++) {
            final int key = k;
            view.setKeyAction(KeyEvent.VK_0 + k, () -> { Ansichten.Ansicht a = Ansichten.taste(key); if (a != null) renderer.placeView(a); });
        }
        view.setKeyAction(KeyEvent.VK_B, () -> renderer.blitzTaste('B'));
        view.setKeyAction(KeyEvent.VK_K, () -> renderer.blitzTaste('K'));
        view.setKeyAction(KeyEvent.VK_T, () -> renderer.blitzTaste('T'));
        view.setKeyAction(KeyEvent.VK_G, renderer::naechsteQualitaet);
        view.setClickListener((x, y, w, h) -> renderer.klick(x, y, w, h));
        renderer.setErrorListener(msg -> SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, msg, "Thunder", JOptionPane.WARNING_MESSAGE)));
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                view.stop();
                renderer.klang.dispose();
                com.dan.thunder.data.StrikeLogger lg = logger;
                if (lg != null) lg.close();
            }
        });
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        maximiert();
        view.start();
        load(offline);
    }

    /**
     * Das FFrame ist durchscheinend (runde Ecken über einen transparenten Hintergrund). Ein schweres {@code GLCanvas}
     * bleibt in so einem Fenster unter Windows unsichtbar, ohne Fehlermeldung. Darum wie in Sylt: das Fenster deckend
     * machen und die Ecken über {@code setShape} runden (maximiert eckig).
     */
    private void deckend() {
        frame.setBackground(new java.awt.Color(FTheme.getInstance().getBackground().getRGB() & 0xFFFFFF));   // ganz deckend
        java.awt.event.ComponentAdapter form = new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { form(); }
            @Override public void componentShown(java.awt.event.ComponentEvent e) { form(); }
        };
        frame.addComponentListener(form);
        frame.addWindowStateListener(e -> form());
    }

    private void form() {
        try {
            boolean max = (frame.getExtendedState() & java.awt.Frame.MAXIMIZED_BOTH) != 0;
            frame.setShape(max ? null : new java.awt.geom.RoundRectangle2D.Double(0, 0, frame.getWidth(), frame.getHeight(), 20, 20));
        } catch (UnsupportedOperationException | java.awt.IllegalComponentStateException e) {
            // Form nicht möglich: dann eben eckig
        }
    }

    /**
     * Beim Start maximiert (wie Sylt): das Fenster über die Arbeitsfläche, und die Titelleiste des FFrame führt ihren
     * eigenen Zustand mit („Wiederherstellen“ stellt die Größe von vorher her).
     */
    private void maximiert() {
        java.awt.Rectangle normal = frame.getBounds();
        frame.setMaximizedBounds(java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());
        frame.setExtendedState(frame.getExtendedState() | java.awt.Frame.MAXIMIZED_BOTH);
        try {
            for (java.lang.reflect.Field fd : FFrame.class.getDeclaredFields()) {
                if (!fd.getType().getSimpleName().equals("FTaskbar")) continue;
                fd.setAccessible(true);
                Object bar = fd.get(frame);
                Class<?> bc = bar.getClass();
                java.lang.reflect.Field mx = bc.getDeclaredField("maximized"), rb = bc.getDeclaredField("restoreBounds"), btn = bc.getDeclaredField("btnMaximize");
                mx.setAccessible(true);
                rb.setAccessible(true);
                btn.setAccessible(true);
                mx.setBoolean(bar, true);
                rb.set(bar, normal);
                Object icon = btn.get(bar);
                icon.getClass().getMethod("setType", FIconType.class).invoke(icon, FIconType.RESTORE);
                ((javax.swing.JComponent) icon).setToolTipText("Wiederherstellen");
                ((java.awt.Component) icon).addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                        SwingUtilities.invokeLater(() -> {
                            if ((frame.getExtendedState() & java.awt.Frame.MAXIMIZED_BOTH) != 0) {
                                frame.setExtendedState(frame.getExtendedState() & ~java.awt.Frame.MAXIMIZED_BOTH);
                                frame.setBounds(normal);
                            }
                        });
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("Thunder: Titelleiste des FFrame nicht angeglichen: " + e);
        }
    }

    private void applyFrameTheme() {
        FTheme th = FTheme.getInstance();
        frame.setTaskbarColors(th.getPrimary(), th.getAccent());
        frame.setIconGlowColor(th.getGlow());
        frame.setComponentPaneColor(th.getBackground());
    }

    private void load(boolean offline) {
        config = DbConfig.load();
        boolean useDb = config.enabled() && !offline;
        renderer.hudMeldung(useDb ? "Verbinde mit Oracle · " + config.describe() + " …" : "Gewitter zieht auf …");
        new SwingWorker<ThunderData, Void>() {
            private String note = "";
            private boolean ausDb;

            @Override
            protected ThunderData doInBackground() {
                if (useDb) {
                    try {
                        ThunderData d = new OracleThunderRepository(config).load(config.scene());
                        ausDb = true;
                        return d;
                    } catch (Exception ex) {
                        note = "Oracle nicht erreichbar (" + shortReason(ex) + ")";
                    }
                }
                return new BuiltinThunderRepository().load(config.scene());
            }

            @Override
            protected void done() {
                try {
                    ThunderData data = get();
                    StormLevel start = data.levels().stream().filter(l -> l.code().equals("STORM")).findFirst().orElse(data.levels().get(0));
                    renderer.setDaten(data);
                    renderer.setLevel(start);
                    if (ausDb && config.logStrikes()) blitzprotokoll(data);
                    bar.setData(data, start);
                    sourceText = data.source() + " · " + data.levels().size() + " Stufen";
                    renderer.hudMeldung(note.isEmpty() ? sourceText : sourceText + " · " + note);
                } catch (Exception ex) {
                    renderer.hudMeldung("Fehler beim Laden: " + ex.getMessage());
                }
            }
        }.execute();
    }

    /** Das Blitzprotokoll (TH_STRIKE): jeder Blitz der Regie mit Stufe, Stimmung, Art, Ort; schreibt alle 3 s. */
    private void blitzprotokoll(ThunderData data) {
        com.dan.thunder.data.StrikeLogger lg = new com.dan.thunder.data.StrikeLogger(config, msg -> renderer.hudMeldung(msg));
        logger = lg;
        renderer.gewitter.onBlitz = (s, b) -> {
            StormLevel l = renderer.gewitter.level();
            lg.log(new com.dan.thunder.data.StrikeLogger.Entry(System.currentTimeMillis(), l == null ? 0 : l.id(), renderer.wiring.mood,
                    s.kind().dbCode(), s.clicked(), s.distKm(), s.peakKa(), s.gx() / 1600.0, s.seed(), b.hitX, b.hitZ, b.kind.name()));
        };
    }

    // ------------------------------------------------------------------ Bedienleiste

    @Override
    public void levelChanged(StormLevel level) { renderer.setLevel(level); }

    @Override
    public void moodChanged(Mood mood) { renderer.setMood(mood, false); }

    @Override
    public void rainChanged(boolean on) { renderer.gewitter.regenAn = on; }

    @Override
    public boolean soundChanged(boolean on) {
        boolean laeuft = renderer.klang.setOn(on);
        if (on && !laeuft) renderer.hudMeldung("Kein Ton: " + renderer.klang.note.replace("!", ""));
        return laeuft == on;
    }

    @Override
    public void softChanged(boolean on) { renderer.gewitter.setSanft(on); }

    @Override
    public void seglerChanged(boolean on) { renderer.showSegler = on; }

    private static String shortReason(Exception ex) {
        String m = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        m = m.replaceAll("\\s+", " ");
        return m.length() > 60 ? m.substring(0, 57) + "…" : m;
    }
}
