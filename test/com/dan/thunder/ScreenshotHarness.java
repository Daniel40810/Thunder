package com.dan.thunder;

import com.dan.thunder.app.ThunderApp;
import com.dan.thunder.model.Mood;
import com.dan.thunder.render.StormCanvas;
import com.dan.thunder.storm.Strike;
import com.dan.thunder.storm.StrikeKind;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.image.BufferedImage;
import java.io.File;

/** Startet die echte App unter Xvfb und speichert Screenshots. Nicht Teil des JARs. */
public final class ScreenshotHarness {

    private static Frame frame;
    private static StormCanvas canvas;

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "shots";
        new File(out).mkdirs();
        ThunderApp.main(new String[] { "--offline" });
        Thread.sleep(2500);
        frame = Frame.getFrames()[0];
        SwingUtilities.invokeAndWait(() -> canvas = find(frame, StormCanvas.class));
        Thread.sleep(1500);
        shot(out + "/0_abend_ruhe.png");

        strike(StrikeKind.CG, 0.42, 2.0);
        shot(out + "/1_abend_einschlag_nah.png");

        SwingUtilities.invokeAndWait(() -> {
            canvas.setMood(Mood.NACHT);
            canvas.setLevel(canvas.getData().level("SEVERE").orElseThrow());
        });
        Thread.sleep(2500);
        strike(StrikeKind.CROWN, 0.6, 1.0);
        shot(out + "/2_nacht_unwetter_krone.png");

        SwingUtilities.invokeAndWait(() -> canvas.setLevel(canvas.getData().level("FAR").orElseThrow()));
        Thread.sleep(3000);
        strike(StrikeKind.IC, 0.3, 6);
        shot(out + "/3_nacht_wetterleuchten.png");

        Thread.sleep(1600);
        strike(StrikeKind.CG, 0.7, 0.8);
        Thread.sleep(90);
        shot(out + "/4_nacht_folgeblitz.png");
        System.exit(0);
    }

    private static void strike(StrikeKind kind, double xf, double dist) throws Exception {
        Strike[] s = new Strike[1];
        SwingUtilities.invokeAndWait(() -> s[0] = canvas.strikeForTest(kind, canvas.getWidth() * xf, dist));
        Thread.sleep((long) s[0].envelope().leadMs() + 25);
    }

    /** Rendert das Fenster direkt (trifft den Blitz-Moment genauer als ein Bildschirmfoto). */
    private static void shot(String file) throws Exception {
        BufferedImage img = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
        SwingUtilities.invokeAndWait(() -> {
            java.awt.Graphics2D g = img.createGraphics();
            ((javax.swing.JFrame) frame).getRootPane().paint(g);
            g.dispose();
        });
        ImageIO.write(img, "png", new File(file));
        System.out.println("saved " + file);
    }

    private static <T> T find(Container c, Class<T> type) {
        for (Component k : c.getComponents()) {
            if (type.isInstance(k)) {
                return type.cast(k);
            }
            if (k instanceof Container cc) {
                T t = find(cc, type);
                if (t != null) {
                    return t;
                }
            }
        }
        return null;
    }
}
