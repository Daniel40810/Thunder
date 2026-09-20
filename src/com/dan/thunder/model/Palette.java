package com.dan.thunder.model;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;

/** Eine Farbstimmung (alle {@link PaletteRole}s). Unveränderlich. */
public final class Palette {

    private final EnumMap<PaletteRole, Color> colors;

    public Palette(Map<PaletteRole, Color> colors) {
        this.colors = new EnumMap<>(PaletteRole.class);
        this.colors.putAll(colors);
        for (PaletteRole r : PaletteRole.values()) {
            if (!this.colors.containsKey(r)) {
                throw new IllegalArgumentException("Farbrolle fehlt: " + r);
            }
        }
    }

    public Color get(PaletteRole role) {
        return colors.get(role);
    }

    /** Überblendung zweier Stimmungen, t = 0 → a, t = 1 → b. */
    public static Palette lerp(Palette a, Palette b, double t) {
        if (t <= 0) {
            return a;
        }
        if (t >= 1) {
            return b;
        }
        EnumMap<PaletteRole, Color> m = new EnumMap<>(PaletteRole.class);
        for (PaletteRole r : PaletteRole.values()) {
            m.put(r, mix(a.get(r), b.get(r), t));
        }
        return new Palette(m);
    }

    public static Color mix(Color a, Color b, double t) {
        return new Color(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                (int) Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    public static Color hex(String s) {
        return new Color(Integer.parseInt(s.substring(1), 16));
    }
}
