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
 * <p>
 * Stand Phase 18a: die Szene (Strand, Düne, Meer mit Brandung, Himmel, Wolken, Leuchtturm, Seebrücke, Strandkörbe),
 * Stufe und Stimmung wirken auf Wind, See, Wolken und Uhr. Blitze, Glutkern, Regen und Klang folgen in 18b, das
 * Blitzprotokoll und die Datenbank in 18c.
 */
public final class ThunderApp implements ControlBar.Actions {

    private final FFrame frame = new FFrame("Thunder · Gewitter über der Ostsee");
    private final Renderer renderer;
    private final SceneView view;
    private final ControlBar bar;
    private final FTheme.ThemeListener themeListener = t -> applyFrameTheme();
    private DbConfig config;
    private String sourceText = "";
    private volatile boolean rain = true, soft;

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
        for (Ansichten.Ansicht a : Ansichten.ALLE) view.setKeyAction(KeyEvent.VK_0 + a.key(), () -> renderer.placeView(a));
        renderer.setErrorListener(msg -> SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(frame, msg, "Thunder", JOptionPane.WARNING_MESSAGE)));
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) { view.stop(); }
        });
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
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

            @Override
            protected ThunderData doInBackground() {
                if (useDb) {
                    try {
                        return new OracleThunderRepository(config).load(config.scene());
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
                    renderer.setLevel(start);
                    bar.setData(data, start);
                    sourceText = data.source() + " · " + data.levels().size() + " Stufen";
                    renderer.hudMeldung(note.isEmpty() ? sourceText : sourceText + " · " + note);
                } catch (Exception ex) {
                    renderer.hudMeldung("Fehler beim Laden: " + ex.getMessage());
                }
            }
        }.execute();
    }

    // ------------------------------------------------------------------ Bedienleiste

    @Override
    public void levelChanged(StormLevel level) { renderer.setLevel(level); }

    @Override
    public void moodChanged(Mood mood) { renderer.setMood(mood, false); }

    @Override
    public void rainChanged(boolean on) { rain = on; }   // Regen ab Phase 18b

    @Override
    public boolean soundChanged(boolean on) {
        if (on) renderer.hudMeldung("Ton kommt mit Phase 18b (soundscape)");
        return !on;
    }

    @Override
    public void softChanged(boolean on) { soft = on; }   // ab Phase 18b: dämpft das Licht der Blitze

    private static String shortReason(Exception ex) {
        String m = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        m = m.replaceAll("\\s+", " ");
        return m.length() > 60 ? m.substring(0, 57) + "…" : m;
    }
}
