package com.dan.thunder.app;

import com.dan.fframe.FFrame;
import com.dan.ficons.FIconType;
import com.dan.fstyle.FTheme;
import com.dan.fstyle.FThemePreset;
import com.dan.thunder.audio.ThunderSynth;
import com.dan.thunder.data.BuiltinThunderRepository;
import com.dan.thunder.data.DbConfig;
import com.dan.thunder.data.OracleThunderRepository;
import com.dan.thunder.data.StrikeLogger;
import com.dan.thunder.model.Mood;
import com.dan.thunder.model.StormLevel;
import com.dan.thunder.model.ThunderData;
import com.dan.thunder.render.StormCanvas;
import com.dan.thunder.storm.StormListener;
import com.dan.thunder.storm.Strike;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;

/**
 * Thunder – ein Sommergewitter über der Ostsee.
 * <p>
 * Start: {@code java -jar Thunder.jar} (liest Oracle, fällt bei Bedarf auf eingebaute Daten zurück)
 * oder {@code java -jar Thunder.jar --offline}.
 */
public final class ThunderApp implements ControlBar.Actions {

    private final FFrame frame = new FFrame("Thunder · Gewitter über der Ostsee");
    private final StormCanvas canvas = new StormCanvas();
    private final ThunderSynth synth = new ThunderSynth();
    private final ControlBar bar;
    private final FTheme.ThemeListener themeListener = t -> applyFrameTheme();
    private DbConfig config;
    private StrikeLogger logger;
    private String sourceText = "";

    private ThunderApp(Mood mood) {
        canvas.setMood(mood);
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
        frame.setLogoType(FIconType.BEACH);
        frame.getComponentPane().add(canvas, BorderLayout.CENTER);
        frame.getComponentPane().add(bar, BorderLayout.SOUTH);
        frame.setPreferredFrameSize(new Dimension(1440, 900));
        frame.setMinimumSize(new Dimension(1180, 720));
        applyFrameTheme();
        FTheme.getInstance().addThemeListener(themeListener);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                synth.stop();
                if (logger != null) {
                    logger.close();
                }
            }
        });
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        load(offline);
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
        canvas.setLoadingText(useDb ? "Verbinde mit Oracle · " + config.describe() + " …" : "Gewitter zieht auf …");
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
                    canvas.setData(data);
                    bar.setData(data, canvas.getDirector().getLevel());
                    boolean fromDb = useDb && note.isEmpty();
                    if (fromDb && config.logStrikes()) {
                        logger = new StrikeLogger(config, msg -> SwingUtilities.invokeLater(() -> setStatus(sourceText + " · " + msg)));
                    }
                    canvas.getDirector().addListener(new StormListener() {
                        @Override
                        public void strikeStarted(Strike s) {
                            logStrike(s);
                        }

                        @Override
                        public void thunderArrived(Strike s) {
                            synth.thunder(s.distKm());
                        }
                    });
                    sourceText = data.source() + " · " + data.levels().size() + " Stufen"
                            + (logger != null ? " · Blitzprotokoll → TH_STRIKE" : "");
                    setStatus(note.isEmpty() ? sourceText : sourceText + " · " + note);
                    updateAmbience();
                } catch (Exception ex) {
                    setStatus("Fehler beim Laden: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void logStrike(Strike s) {
        if (logger == null) {
            return;
        }
        StormLevel lvl = canvas.getDirector().getLevel();
        double posX = (s.kind() == com.dan.thunder.storm.StrikeKind.IC ? s.cx() : s.gx()) / Math.max(1, canvas.getWidth());
        logger.log(new StrikeLogger.Entry(System.currentTimeMillis(), lvl.id(), canvas.getMood(), s.kind().dbCode(),
                s.clicked(), s.distKm(), s.peakKa(), posX, s.seed()));
    }

    private void setStatus(String text) {
        canvas.setStatusText(text);
    }

    private void updateAmbience() {
        if (canvas.getDirector() == null) {
            return;
        }
        StormLevel l = canvas.getDirector().getLevel();
        synth.setRain(canvas.isRainVisible() ? Math.min(1, 0.25 + l.rainDrops() / 950.0 * 0.75) : 0);
        synth.setSurf(0.35 + 0.65 * Math.min(1, l.wind()));
    }

    // ------------------------------------------------------------------ Bedienleiste

    @Override
    public void levelChanged(StormLevel level) {
        canvas.setLevel(level);
        updateAmbience();
    }

    @Override
    public void moodChanged(Mood mood) {
        canvas.setMood(mood);
    }

    @Override
    public void rainChanged(boolean on) {
        canvas.setRainVisible(on);
        updateAmbience();
    }

    @Override
    public boolean soundChanged(boolean on) {
        if (!on) {
            synth.stop();
            return true;
        }
        updateAmbience();
        if (synth.start()) {
            return true;
        }
        setStatus(sourceText + " · Kein Ton: " + synth.error());
        return false;
    }

    @Override
    public void softChanged(boolean on) {
        canvas.setSoft(on);
    }

    private static String shortReason(Exception ex) {
        String m = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        m = m.replaceAll("\\s+", " ");
        return m.length() > 60 ? m.substring(0, 57) + "…" : m;
    }
}
