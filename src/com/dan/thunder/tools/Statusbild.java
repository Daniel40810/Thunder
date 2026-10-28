package com.dan.thunder.tools;

import com.dan.thunder.camera.Ansichten;
import com.dan.thunder.data.BuiltinThunderRepository;
import com.dan.thunder.gl.Quality;
import com.dan.thunder.gl.Renderer;
import com.dan.thunder.model.Mood;
import com.dan.thunder.model.StormLevel;
import com.dan.thunder.model.ThunderData;
import com.dan.thunder.scene.SceneClock;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/**
 * Statusbild ohne Fenster (Phase 18): oben die fünf Bilder des 2D-Thunder aus {@code docs/}, darunter derselbe Blick in
 * 3D mit derselben Stufe und Stimmung; dazu auf Wunsch die Ansichten 2 bis 5.
 * <pre>
 * Statusbild datei.png [breite] [NIEDRIG|MITTEL|HOCH|ULTRA] [titel] [ansichten]
 * </pre>
 */
public final class Statusbild {
    private Statusbild() { }

    /** Die fünf Bilder in docs/: Datei, Stufe (Code), Stimmung, Beschriftung. */
    static final String[][] BILDER = {
            {"0_abend_ruhe", "STORM", "ABEND", "0 · Abend, Gewitter, zwischen zwei Blitzen"},
            {"1_abend_einschlag_nah", "STORM", "ABEND", "1 · Abend, Gewitter, naher Einschlag"},
            {"2_nacht_unwetter_krone", "SEVERE", "NACHT", "2 · Nacht, Unwetter, Blitzkrone"},
            {"3_nacht_wetterleuchten", "FAR", "NACHT", "3 · Nacht, Wetterleuchten"},
            {"4_nacht_folgeblitz", "FAR", "NACHT", "4 · Nacht, Folgeblitz vor der Seebrücke"}};

    public static void main(String[] a) throws Exception {
        File out = new File(a.length > 0 ? a[0] : "docs/status/Status_Phase18a_claude.png");
        int width = a.length > 1 ? Integer.parseInt(a[1]) : 2000;
        Quality q = a.length > 2 ? Quality.valueOf(a[2]) : Quality.HOCH;
        String titel = a.length > 3 ? a[3] : "Thunder " + Renderer.VERSION;
        boolean ansichten = a.length > 4 && a[4].equals("ansichten");
        ThunderData data = new BuiltinThunderRepository().load("OSTSEE");
        int n = BILDER.length, cw = width / n, ch = cw * 9 / 16, top = 34;
        int rows = ansichten ? 3 : 2;
        BufferedImage img = new BufferedImage(cw * n, top + ch * rows, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setColor(new Color(16, 20, 30));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setColor(new Color(235, 230, 220));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        g.drawString(titel + "   (oben: 2D-Thunder aus docs/, darunter derselbe Blick in 3D)", 12, 24);
        // oben die alten Bilder (ohne Titelleiste und Bedienleiste: die Bildfläche ist 18..390 von 450 → 4 % bis 86,7 %)
        for (int i = 0; i < n; i++) {
            BufferedImage alt = ImageIO.read(new File("docs/" + BILDER[i][0] + ".png"));
            int y0 = (int) Math.round(alt.getHeight() * 18 / 450.0), y1 = (int) Math.round(alt.getHeight() * 390 / 450.0);
            BufferedImage teil = alt.getSubimage(0, y0, alt.getWidth(), y1 - y0);
            g.drawImage(teil, i * cw, top, cw, ch, null);
            label(g, BILDER[i][3], i * cw, top);
        }

        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        int rw = Math.max(cw, 960), rh = rw * 9 / 16;
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, rw, rh);
        d.setAutoSwapBufferMode(false);
        SceneClock clock = new SceneClock();
        clock.lapse = 0;
        Renderer r = new Renderer(clock);
        r.frozen = true;
        r.showHud = false;
        r.autoScale = false;
        r.setQuality(q);
        d.addGLEventListener(r);
        d.display();
        if (r.error() != null) { System.err.println("FEHLER: " + r.error()); System.exit(2); }
        for (int i = 0; i < n; i++) {
            StormLevel l = level(data, BILDER[i][1]);
            r.setLevel(l);
            r.setMood(Mood.valueOf(BILDER[i][2]), true);
            r.placeView(Ansichten.taste(1));
            BufferedImage b = render(d, r, 12);
            g.drawImage(b, i * cw, top + ch, cw, ch, null);
            label(g, String.format(Locale.GERMAN, "3D · %s · %s · %s", l.name(), BILDER[i][2].equals("NACHT") ? "Nacht" : "Abend", clock.timeLabel()), i * cw, top + ch);
            System.out.println(BILDER[i][3] + " · " + r.wiring.zeile() + " · " + r.brandung.meldung);
        }
        if (ansichten) {
            r.setLevel(level(data, "STORM"));
            r.setMood(Mood.ABEND, true);
            for (int k = 2; k <= 5; k++) {
                Ansichten.Ansicht an = Ansichten.taste(k);
                r.placeView(an);
                BufferedImage b = render(d, r, 6);
                int i = k - 2;
                g.drawImage(b, i * cw, top + 2 * ch, cw, ch, null);
                label(g, "Ansicht " + k + ": " + an.name(), i * cw, top + 2 * ch);
            }
        }
        g.dispose();
        File dir = out.getAbsoluteFile().getParentFile();
        if (dir != null) dir.mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("Bild: " + out.getPath());
        d.destroy();
        System.exit(0);
    }

    static StormLevel level(ThunderData data, String code) {
        for (StormLevel l : data.levels()) if (l.code().equals(code)) return l;
        return data.levels().get(0);
    }

    /** Vorlauf (Sekunden Szenenzeit), dann ein Bild. */
    static BufferedImage render(GLOffscreenAutoDrawable d, Renderer r, double vorlauf) {
        r.camera.snap();
        r.step(1 / 30.0);
        r.brandungJetzt();
        for (int i = 0; i < vorlauf * 30; i++) r.step(1 / 30.0);
        r.readapt();
        r.camera.snap();
        for (int i = 0; i < 16; i++) d.display();
        final BufferedImage[] img = new BufferedImage[1];
        d.invoke(true, x -> { img[0] = r.grab(x); return true; });
        return img[0];
    }

    static void label(Graphics2D g, String s, int x, int y) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        int w = g.getFontMetrics().stringWidth(s) + 12;
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(x + 4, y + 4, w, 20);
        g.setColor(new Color(240, 236, 226));
        g.drawString(s, x + 10, y + 19);
    }
}
