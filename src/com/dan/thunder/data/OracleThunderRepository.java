package com.dan.thunder.data;

import com.dan.thunder.model.Mood;
import com.dan.thunder.model.Palette;
import com.dan.thunder.model.PaletteRole;
import com.dan.thunder.model.Scene;
import com.dan.thunder.model.SceneObject;
import com.dan.thunder.model.StormLevel;
import com.dan.thunder.model.ThunderData;

import java.awt.Color;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Liest die TH_-Tabellen aus dem Schema DEMO über ojdbc11. */
public final class OracleThunderRepository implements ThunderRepository {

    private final DbConfig config;

    public OracleThunderRepository(DbConfig config) {
        this.config = config;
    }

    @Override
    public ThunderData load(String sceneCode) throws SQLException {
        DriverManager.setLoginTimeout(config.timeoutSeconds());
        try (Connection con = DriverManager.getConnection(config.url(), config.user(), config.password())) {
            con.setReadOnly(true);
            List<StormLevel> levels = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT LEVEL_ID, CODE, NAME_DE, RATE_SEC, SHARE_IC, SHARE_CROWN, DIST_MIN_KM, DIST_MAX_KM, "
                    + "RAIN_DROPS, WIND, EMBER, DESCRIPTION FROM TH_STORM_LEVEL ORDER BY SORT_ORDER");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    levels.add(new StormLevel(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getDouble(4),
                            rs.getDouble(5), rs.getDouble(6), rs.getDouble(7), rs.getDouble(8), rs.getInt(9),
                            rs.getDouble(10), rs.getDouble(11), rs.getString(12)));
                }
            }
            if (levels.isEmpty()) {
                throw new SQLException("TH_STORM_LEVEL ist leer – bitte sql/00_th_install.sql ausführen");
            }
            Scene scene = loadScene(con, sceneCode);
            Map<Mood, Palette> palettes = loadPalettes(con);
            return new ThunderData(levels, scene, palettes, "Oracle " + config.describe());
        }
    }

    private static Scene loadScene(Connection con, String code) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT SCENE_ID, CODE, NAME_DE, HORIZON_Y, SHORE_Y, EMBER_X, EMBER_Y FROM TH_SCENE WHERE CODE = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Szene " + code + " fehlt in TH_SCENE");
                }
                long id = rs.getLong(1);
                String sc = rs.getString(2), name = rs.getString(3);
                double hor = rs.getDouble(4), shore = rs.getDouble(5), ex = rs.getDouble(6), ey = rs.getDouble(7);
                List<SceneObject> objects = new ArrayList<>();
                try (PreparedStatement po = con.prepareStatement(
                        "SELECT OBJ_TYPE, POS_X, DEPTH, OBJ_SCALE, COLOR_HEX FROM TH_SCENE_OBJECT "
                        + "WHERE SCENE_ID = ? ORDER BY SORT_ORDER")) {
                    po.setLong(1, id);
                    try (ResultSet ro = po.executeQuery()) {
                        while (ro.next()) {
                            String hex = ro.getString(5);
                            objects.add(new SceneObject(SceneObject.Type.valueOf(ro.getString(1)), ro.getDouble(2),
                                    ro.getDouble(3), ro.getDouble(4), hex == null ? null : Palette.hex(hex)));
                        }
                    }
                }
                return new Scene(sc, name, hor, shore, ex, ey, List.copyOf(objects));
            }
        }
    }

    private static Map<Mood, Palette> loadPalettes(Connection con) throws SQLException {
        Map<Mood, Map<PaletteRole, Color>> raw = new EnumMap<>(Mood.class);
        try (PreparedStatement ps = con.prepareStatement("SELECT MOOD, ROLE, COLOR_HEX FROM TH_PALETTE");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                PaletteRole role;
                try {
                    role = PaletteRole.valueOf(rs.getString(2).trim());
                } catch (IllegalArgumentException unknown) {
                    continue; // zusätzliche Rollen stören nicht
                }
                raw.computeIfAbsent(Mood.valueOf(rs.getString(1).trim()), m -> new EnumMap<>(PaletteRole.class))
                        .put(role, Palette.hex(rs.getString(3)));
            }
        }
        Map<Mood, Palette> out = new EnumMap<>(Mood.class);
        for (Mood m : Mood.values()) {
            Map<PaletteRole, Color> colors = raw.get(m);
            if (colors == null) {
                throw new SQLException("TH_PALETTE: Stimmung " + m + " fehlt");
            }
            out.put(m, new Palette(colors));
        }
        return out;
    }
}
