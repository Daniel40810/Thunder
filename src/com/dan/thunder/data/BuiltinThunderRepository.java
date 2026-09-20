package com.dan.thunder.data;

import com.dan.thunder.model.Mood;
import com.dan.thunder.model.Palette;
import com.dan.thunder.model.PaletteRole;
import com.dan.thunder.model.Scene;
import com.dan.thunder.model.SceneObject;
import com.dan.thunder.model.SceneObject.Type;
import com.dan.thunder.model.StormLevel;
import com.dan.thunder.model.ThunderData;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Rückfall ohne Datenbank. Die Werte entsprechen genau sql/02_th_data.sql.
 */
public final class BuiltinThunderRepository implements ThunderRepository {

    @Override
    public ThunderData load(String sceneCode) {
        List<StormLevel> levels = List.of(
                new StormLevel(1, "FAR", "Wetterleuchten", 5.5, 0.70, 0.00, 5.0, 14.0, 140, 0.35, 0.35,
                        "Fernes Gewitter über der See, meist Blitze in der Wolke, kaum Regen"),
                new StormLevel(2, "STORM", "Gewitter", 3.0, 0.35, 0.05, 1.2, 9.0, 480, 0.65, 0.55,
                        "Gewitter zieht über die Bucht, Einschläge im Wasser, Regen"),
                new StormLevel(3, "SEVERE", "Unwetter", 1.6, 0.25, 0.20, 0.35, 6.0, 950, 1.00, 1.00,
                        "Zelle direkt über dem Strand, Blitzkronen, Starkregen und Sturmböen"));
        Scene scene = new Scene("OSTSEE", "Ostseestrand mit Leuchtturm und Seebrücke", 0.600, 0.800, 0.620, 0.330, List.of(
                new SceneObject(Type.LEUCHTTURM, 0.140, 0.000, 1.00, Palette.hex("#C8102E")),
                new SceneObject(Type.SEEBRUECKE, 0.780, 0.000, 1.00, null),
                new SceneObject(Type.DUENE, 0.000, 1.000, 1.00, null),
                new SceneObject(Type.STRANDKORB, 0.430, 0.220, 0.70, Palette.hex("#3C64BE")),
                new SceneObject(Type.STRANDKORB, 0.510, 0.400, 0.82, Palette.hex("#2E8B57")),
                new SceneObject(Type.STRANDKORB, 0.370, 0.620, 1.00, Palette.hex("#3C64BE")),
                new SceneObject(Type.STRANDKORB, 0.600, 0.780, 1.15, Palette.hex("#C8102E"))));
        Map<Mood, Palette> pal = new EnumMap<>(Mood.class);
        pal.put(Mood.NACHT, palette(
                "#04060D", "#0A1024", "#2C2335", "#AA6E78", "#FF9A50",
                "#111628", "#5C7CC0", "#080A14", "#1E2038", "#C4D4FF",
                "#141A30", "#03050B", "#0D0B0A", "#050608", "#7896FF", "#AFC3EB", "#FFB070"));
        pal.put(Mood.ABEND, palette(
                "#10152A", "#2A2A48", "#A8583E", "#FFB070", "#FFA04C",
                "#221F33", "#8C96CC", "#15121C", "#5A3E4E", "#FFE4C8",
                "#4A3440", "#0B0910", "#211913", "#0A0808", "#A0B4FF", "#D8C8C0", "#FFC080"));
        return new ThunderData(levels, scene, pal, "Eingebaute Daten");
    }

    /** Farben in der Reihenfolge von {@link PaletteRole}. */
    private static Palette palette(String... hex) {
        PaletteRole[] roles = PaletteRole.values();
        Map<PaletteRole, java.awt.Color> m = new EnumMap<>(PaletteRole.class);
        for (int i = 0; i < roles.length; i++) {
            m.put(roles[i], Palette.hex(hex[i]));
        }
        return new Palette(m);
    }
}
