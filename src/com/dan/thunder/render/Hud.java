package com.dan.thunder.render;

import com.dan.fstyle.FFontRole;
import com.dan.fstyle.FTheme;
import com.dan.thunder.storm.Strike;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.Locale;

/**
 * Einblendungen über der Szene: Titel, Blitzprotokoll-Karte (Typ, Entfernung, Stromstärke,
 * Countdown bis zum Donner), Statuszeile und Bedienhinweis. Eigener Szenen-Look, Schrift aus FTheme.
 */
final class Hud {

    private static final Color TEXT = new Color(238, 243, 255);
    private static final Color MUTED = new Color(160, 170, 196);
    private static final Color EMBER = new Color(255, 138, 61);
    private static final Color CARD = new Color(11, 16, 32, 160);
    private static final Color RIM = new Color(207, 224, 255, 56);

    void paint(Graphics2D g, double w, double h, String subtitle, Strike last, double nowMs, int perMinute,
               String status, String hint) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        FTheme th = FTheme.getInstance();
        Font base = th.getFont(FFontRole.BODY);
        String family = base.getFamily();

        // Titel
        Font title = new Font(family, Font.BOLD, (int) Math.max(40, Math.min(84, h * 0.085)));
        g.setFont(title);
        int tx = 32, ty = 26 + g.getFontMetrics().getAscent();
        shadowText(g, "THUNDER", tx, ty, TEXT, new Color(170, 195, 255, 70));
        g.setFont(new Font(family, Font.PLAIN, 14));
        shadowText(g, subtitle, tx + 2, ty + 24, new Color(206, 214, 234), new Color(0, 0, 0, 200));

        // Blitzprotokoll
        int cw = 250, ch = 150, cx = (int) w - cw - 28, cy = 26;
        RoundRectangle2D card = new RoundRectangle2D.Double(cx, cy, cw, ch, 14, 14);
        g.setColor(CARD);
        g.fill(card);
        g.setColor(RIM);
        g.setStroke(new BasicStroke(1f));
        g.draw(card);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        g.setColor(EMBER);
        g.drawString("LETZTER BLITZ", cx + 14, cy + 22);
        g.setFont(new Font(family, Font.BOLD, 20));
        g.setColor(TEXT);
        g.drawString(last == null ? "–" : last.kind().label().toUpperCase(Locale.GERMANY), cx + 14, cy + 48);
        String[][] rows = {
            { "Entfernung", last == null ? "–" : fmt(last.distKm(), 1) + " km" },
            { "Stromstärke", last == null || last.peakKa() == null ? "–" : (last.peakKa() > 0 ? "+" : "") + last.peakKa() + " kA" },
            { "Donner in", last == null ? "–" : thunderText(last, nowMs) },
            { "Blitze / min", String.valueOf(perMinute) },
        };
        Font label = new Font(family, Font.PLAIN, 13), value = new Font(Font.MONOSPACED, Font.PLAIN, 13);
        int ry = cy + 72;
        for (String[] r : rows) {
            g.setFont(label);
            g.setColor(MUTED);
            g.drawString(r[0], cx + 14, ry);
            g.setFont(value);
            g.setColor(TEXT);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(r[1], cx + cw - 14 - fm.stringWidth(r[1]), ry);
            ry += 18;
        }
        double prog = last == null ? 0 : Math.max(0, Math.min(1, (nowMs - last.bornMs()) / last.thunderDelayMs()));
        int bx = cx + 14, by = cy + ch - 12, bw = cw - 28;
        g.setColor(new Color(207, 224, 255, 30));
        g.fillRoundRect(bx, by, bw, 3, 3, 3);
        if (prog > 0) {
            g.setPaint(new GradientPaint(bx, 0, EMBER, bx + bw, 0, new Color(255, 211, 168)));
            g.fillRoundRect(bx, by, (int) (bw * prog), 3, 3, 3);
        }

        // Status + Hinweis
        g.setFont(new Font(family, Font.PLAIN, 12));
        if (status != null && !status.isEmpty()) {
            shadowText(g, status, 32, (int) h - 16, MUTED, new Color(0, 0, 0, 200));
        }
        if (hint != null) {
            int hw = g.getFontMetrics().stringWidth(hint);
            shadowText(g, hint, (int) w - 28 - hw, (int) h - 16, MUTED, new Color(0, 0, 0, 200));
        }
    }

    private static String thunderText(Strike s, double nowMs) {
        double rem = (s.thunderAtMs() - nowMs) / 1000;
        return rem > 0 ? fmt(rem, 1) + " s" : "gehört";
    }

    private static String fmt(double v, int digits) {
        return String.format(Locale.GERMANY, "%." + digits + "f", v);
    }

    private static void shadowText(Graphics2D g, String s, int x, int y, Color c, Color shadow) {
        g.setColor(shadow);
        g.drawString(s, x + 1, y + 2);
        g.setColor(c);
        g.drawString(s, x, y);
    }
}
