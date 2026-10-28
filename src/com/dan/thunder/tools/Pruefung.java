package com.dan.thunder.tools;

import com.dan.lightning.Bolt;
import com.dan.thunder.camera.Ansichten;
import com.dan.thunder.data.BuiltinThunderRepository;
import com.dan.thunder.data.DbConfig;
import com.dan.thunder.data.OracleThunderRepository;
import com.dan.thunder.gl.Renderer;
import com.dan.thunder.model.SceneObject;
import com.dan.thunder.model.StormLevel;
import com.dan.thunder.model.ThunderData;
import com.dan.thunder.model.ViewDef;
import com.dan.thunder.scene.Bauwerke;
import com.dan.thunder.scene.Gewitter;
import com.dan.thunder.scene.Lage;
import com.dan.thunder.scene.SceneClock;
import com.dan.thunder.scene.Site;
import com.dan.thunder.storm.StrikeKind;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Die Prüfung von Thunder in 3D (Phase 18c), ohne Fenster und ohne Grafikkarte:
 * <ol>
 * <li><b>Daten</b>: die Lage in Metern und die Ansichten in {@code sql/04_th_3d.sql} = eingebaute Daten = Code
 * ({@code scene.Lage}, {@code camera.Ansichten}).</li>
 * <li><b>Regie im Raum</b>: je Stufe zehn Minuten Gewitter. Blitze je Minute wie RATE_SEC, Anteile der Arten wie
 * SHARE_IC und SHARE_CROWN, Erdblitze im Blickfeld und im Abstand der Stufe, Kronen mit 4 bis 7 Spinnenblitzen.</li>
 * <li><b>Glutkern</b>: in der Wolke (über der Untergrenze), in der Peilung aus EMBER_X, er atmet.</li>
 * <li><b>Regen</b> je Stufe, <b>Sanfte Blitze</b> (ein Drittel des Lichts).</li>
 * <li><b>Bedienung</b>: Klick in Himmel und See, Tasten B, K, T, das Beben bei nahem Donner, Ansichten aus den Daten.</li>
 * <li><b>Klang</b>: ein Donner aus 1 km kommt nach rund 3 s an und ist zu hören.</li>
 * <li><b>Bauten</b>: vier Körbe, Pfähle der Seebrücke im Wasser.</li>
 * <li><b>Datenbank</b> (wenn erreichbar und nicht {@code --offline}): {@code 04_th_3d.sql} gelaufen, Lage und Ansichten
 * wie im Code.</li>
 * </ol>
 * Die letzte Zeile ist „Thunder · Prüfung: alles in Ordnung“ oder die Zahl der Fehler; das Protokoll steht in
 * {@code docs/status/Pruefung_Phase18c.txt}.
 */
public final class Pruefung {
    private Pruefung() { }

    static int pruefungen, fehler;
    static PrintWriter pw;

    static void say(String s) {
        System.out.println(s);
        if (pw != null) { pw.println(s); pw.flush(); }
    }

    static void check(boolean ok, String was) {
        pruefungen++;
        if (!ok) fehler++;
        say((ok ? "  ok      " : "  FEHLER  ") + was);
    }

    public static void main(String[] a) throws Exception {
        boolean offline = List.of(a).contains("--offline");
        File out = new File("docs/status/Pruefung_Phase18c.txt");
        out.getAbsoluteFile().getParentFile().mkdirs();
        pw = new PrintWriter(Files.newBufferedWriter(out.toPath(), StandardCharsets.UTF_8));
        say("Thunder · Prüfung · " + Renderer.VERSION + " · " + java.time.LocalDateTime.now().withNano(0));
        ThunderData data = new BuiltinThunderRepository().load("OSTSEE");

        daten(data);
        regie(data);
        glutUndRegen(data);
        bedienung(data);
        klang(data);
        bauten();
        if (!offline) datenbank();
        else say("Datenbank: übersprungen (--offline)");

        say(fehler == 0 ? String.format(Locale.GERMAN, "Thunder · Prüfung: alles in Ordnung (%d Prüfungen)", pruefungen)
                : String.format(Locale.GERMAN, "Thunder · Prüfung: %d Fehler in %d Prüfungen", fehler, pruefungen));
        pw.close();
        System.exit(fehler == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ 1 Daten

    static void daten(ThunderData data) throws Exception {
        say("Daten: sql/04_th_3d.sql = eingebaut = Code");
        String sql = Files.readString(Path.of("sql/04_th_3d.sql"), StandardCharsets.UTF_8);
        // Objekte: SELECT n, x, z, rot ... -- Name
        Matcher m = Pattern.compile("SELECT\\s+(\\d)(?:\\s+SORT_ORDER)?,\\s+(-?[\\d.]+)(?:\\s+X_M)?,\\s+(-?[\\d.]+)(?:\\s+Z_M)?,\\s+(-?[\\d.]+)(?:\\s+ROT_DEG)?\\s+FROM DUAL").matcher(sql);
        List<SceneObject> obj = data.scene().objects();
        int n = 0;
        while (m.find()) {
            int sort = Integer.parseInt(m.group(1));
            SceneObject o = obj.get(sort - 1);
            boolean gleich = o.hatLage() && near(o.xM(), m.group(2)) && near(o.zM(), m.group(3)) && near(o.rotDeg(), m.group(4));
            check(gleich, String.format(Locale.GERMAN, "Objekt %d (%s): SQL %s / %s / %s°, eingebaut %s", sort, o.type(), m.group(2), m.group(3), m.group(4),
                    o.hatLage() ? String.format(Locale.ROOT, "%.2f / %.2f / %.1f°", o.xM(), o.zM(), o.rotDeg()) : "ohne Lage"));
            n++;
        }
        check(n == 7, "sieben Objekte mit Lage im SQL (" + n + ")");
        // Code: Leuchtturm, Seebrücke, Körbe
        Lage.Objekt t = Lage.leuchtturm(), b = Lage.seebruecke();
        check(Math.abs(obj.get(0).xM() - t.xM()) < 0.01 && Math.abs(obj.get(0).zM() - t.zM()) < 0.01, "Leuchtturm eingebaut = Code (Site.LEUCHTTURM)");
        check(Math.abs(obj.get(1).rotDeg() - b.rotDeg()) < 0.05, String.format(Locale.GERMAN, "Seebrücke: Kurs %.1f° zum Kopf", b.rotDeg()));
        // Ansichten: SELECT k, 'Name', x, z, h, 'BEZUG', kurs, neigung, fovy
        Matcher v = Pattern.compile("SELECT\\s+(\\d)(?:\\s+KEY_NO)?,\\s+'([^']+)'(?:\\s+NAME_DE)?,\\s+(-?[\\d.]+)(?:\\s+X_M)?,\\s+(-?[\\d.]+)(?:\\s+Z_M)?,\\s+(-?[\\d.]+)(?:\\s+ABOVE_M)?,\\s+'(\\w+)'(?:\\s+REFERENCE)?,\\s+(-?[\\d.]+)(?:\\s+HEADING_DEG)?,\\s+(-?[\\d.]+)(?:\\s+PITCH_DEG)?,\\s+(-?[\\d.]+)").matcher(sql);
        int k = 0;
        while (v.find()) {
            ViewDef d = data.scene().views().get(Integer.parseInt(v.group(1)) - 1);
            boolean gleich = d.name().equals(v.group(2)) && near(d.xM(), v.group(3)) && near(d.zM(), v.group(4)) && near(d.aboveM(), v.group(5))
                    && d.reference().equals(v.group(6)) && near(d.headingDeg(), v.group(7)) && near(d.pitchDeg(), v.group(8)) && near(d.fovyDeg(), v.group(9));
            check(gleich, "Ansicht " + v.group(1) + " „" + v.group(2) + "“ im SQL = eingebaut");
            k++;
        }
        check(k == 5, "fünf Ansichten im SQL (" + k + ")");
    }

    static boolean near(double a, String b) { return Math.abs(a - Double.parseDouble(b)) < 0.006; }

    // ------------------------------------------------------------------ 2 Regie im Raum

    static void regie(ThunderData data) {
        say("Regie im Raum: je Stufe 10 Minuten");
        for (StormLevel l : data.levels()) {
            Gewitter g = new Gewitter();
            g.setDaten(data.palette(com.dan.thunder.model.Mood.ABEND), data.palette(com.dan.thunder.model.Mood.NACHT), data.scene(), com.dan.thunder.model.Mood.ABEND);
            g.setLevel(l);
            g.regenAn = false;
            List<double[]> cg = new ArrayList<>();
            int[] arten = new int[3];
            final int[] kronen = {0};
            g.onBlitz = (s, b) -> {
                arten[s.kind().ordinal()]++;
                if (s.kind() == StrikeKind.CG) cg.add(new double[]{b.hitX, b.hitZ, s.distKm(), b.attractor});
                if (s.kind() == StrikeKind.CROWN) kronen[0]++;
            };
            double[] auge = {0, 9.6, 0};
            double dt = 1 / 30.0, dauer = 600;
            for (double t = 0; t < dauer; t += dt) g.schritt(dt, null, false, auge, 8, 225);
            int summe = arten[0] + arten[1] + arten[2];
            double jeMin = summe / (dauer / 60), soll = 60 / l.rateSec();
            check(Math.abs(jeMin - soll) < 0.25 * soll, String.format(Locale.GERMAN, "%s: %.1f Blitze je Minute (Stufe %.1f)", l.name(), jeMin, soll));
            double ic = arten[StrikeKind.IC.ordinal()] / (double) summe, kr = arten[StrikeKind.CROWN.ordinal()] / (double) summe;
            check(Math.abs(ic - l.shareIc()) < 0.1 && Math.abs(kr - l.shareCrown()) < 0.08,
                    String.format(Locale.GERMAN, "%s: in der Wolke %.0f %% (Stufe %.0f %%), Kronen %.0f %% (Stufe %.0f %%)", l.name(), ic * 100, l.shareIc() * 100, kr * 100, l.shareCrown() * 100));
            // Erdblitze: im Blickfeld (±HFOV/2 um den Hauptblick) und im Abstand der Stufe (ein Fang durch den Turm zählt mit)
            int imFeld = 0, imAbstand = 0;
            for (double[] c : cg) {
                double peil = Lage.kurs(0, 0, c[0], c[1]), d = ((peil - Site.BLICK + 540) % 360) - 180, km = Math.hypot(c[0], c[1]) / 1000;
                if (Math.abs(d) <= Gewitter.HFOV / 2 + 3 || c[3] >= 0) imFeld++;
                if ((km >= l.distMinKm() * 0.85 - 0.15 && km <= l.distMaxKm() * 1.1 + 0.2) || c[3] >= 0) imAbstand++;
            }
            check(!cg.isEmpty() && imFeld == cg.size() && imAbstand >= cg.size() * 0.95,
                    String.format(Locale.GERMAN, "%s: %d Erdblitze, %d im Blickfeld, %d im Abstand %.1f bis %.1f km", l.name(), cg.size(), imFeld, imAbstand, l.distMinKm(), l.distMaxKm()));
        }
        // die Krone von Hand: 4 bis 7 Spinnenblitze um den Glutkern
        Gewitter g = new Gewitter();
        g.setLevel(data.levels().get(2));
        int n0 = g.blitz.flashes();
        g.krone();
        g.kroneSofort();
        int n = g.blitz.flashes() - n0;
        boolean spinnen = true, nah = true;
        for (Bolt b : g.blitz.bolts()) {
            spinnen &= b.kind == Bolt.Kind.SPIDER;
            nah &= Math.hypot(b.hitX - g.glutOrt[0], b.hitZ - g.glutOrt[2]) < 1600;
        }
        check(n >= 4 && n <= 7 && spinnen && nah, "Krone: " + n + " Spinnenblitze, alle innerhalb 1,6 km um den Glutkern");
    }

    // ------------------------------------------------------------------ 3 Glutkern und Regen

    static void glutUndRegen(ThunderData data) {
        say("Glutkern, Regen, Sanfte Blitze");
        Gewitter g = new Gewitter();
        g.setDaten(data.palette(com.dan.thunder.model.Mood.ABEND), data.palette(com.dan.thunder.model.Mood.NACHT), data.scene(), com.dan.thunder.model.Mood.ABEND);
        g.setLevel(data.levels().get(1));
        double[] o = g.glutOrt;
        double peil = Lage.kurs(0, 0, o[0], o[2]), soll = Site.BLICK + (data.scene().emberX() - 0.5) * Gewitter.HFOV;
        check(Math.abs(peil - soll) < 0.1, String.format(Locale.GERMAN, "Glutkern in Peilung %.1f° (aus EMBER_X %.2f)", peil, data.scene().emberX()));
        double base = com.dan.cloud.CloudType.CUMULONIMBUS.base;
        check(o[1] > base && o[1] < base + com.dan.cloud.CloudType.CUMULONIMBUS.maxThickness, String.format(Locale.GERMAN, "Glutkern in der Wolke: %.0f m (Untergrenze %.0f m)", o[1], base));
        double lo = 1e9, hi = 0;
        g.glutBezug = 1;
        g.regenAn = false;
        g.regieAn = false;
        for (double t = 0; t < 20; t += 0.05) {
            g.schritt(0.05, null, false, new double[]{0, 9.6, 0}, 8, 225);
            lo = Math.min(lo, g.glutJetzt);
            hi = Math.max(hi, g.glutJetzt);
        }
        check(lo > 0 && hi / lo > 1.3 && hi / lo < 3, String.format(Locale.GERMAN, "Glutkern atmet: %.2f bis %.2f (× Anpassung)", lo, hi));
        double[] f = g.farbe(com.dan.thunder.model.PaletteRole.EMBER);
        check(f[0] > f[1] && f[1] > f[2], "Glutkern in der Farbe EMBER (orange)");
        double[] soll2 = {1.4, 10, 30};
        for (int i = 0; i < 3; i++) {
            StormLevel l = data.levels().get(i);
            double r = Gewitter.regenRate(l);
            check(Math.abs(r - soll2[i]) < 0.3, String.format(Locale.GERMAN, "Regen %s: %.1f mm/h", l.name(), r));
        }
        g.setSanft(true);
        g.schritt(0.05, null, false, new double[]{0, 9.6, 0}, 8, 225);
        check(Math.abs(g.blitz.candelaPerMetre - Gewitter.KANAL_CD / 3) < 1 && g.regie().isSoft(), "Sanfte Blitze: ein Drittel des Lichts, die Regie gibt eine Entladung");
    }

    // ------------------------------------------------------------------ 4 Bedienung

    static void bedienung(ThunderData data) {
        say("Bedienung: Klick, Tasten, Beben, Ansichten");
        Renderer r = new Renderer(new SceneClock());
        r.setDaten(data);
        r.setLevel(data.levels().get(1));
        r.camera.home();
        r.camera.snap();
        List<double[]> klicks = new ArrayList<>();
        List<Boolean> geklickt = new ArrayList<>();
        r.gewitter.onBlitz = (s, b) -> { klicks.add(new double[]{b.hitX, b.hitZ, s.distKm()}); geklickt.add(s.clicked()); };
        int w = 1600, h = 900;
        double[] oben = r.klick(w / 2, 40, w, h), kimm = r.klick(w / 2, (int) (h * 0.405), w, h), see = r.klick(w / 2, (int) (h * 0.62), w, h);
        check(oben[1] < kimm[1], String.format(Locale.GERMAN, "Klick hoch in den Himmel näher (%.1f km) als an der Kimm (%.1f km)", oben[1], kimm[1]));
        check(see[1] < 0.5 && Math.abs(see[0] - Site.BLICK) < 2, String.format(Locale.GERMAN, "Klick auf See und Strand: Einschlag dort (%.0f m, %.1f°)", see[1] * 1000, see[0]));
        check(klicks.size() == 3 && !geklickt.contains(false), "drei Klicks, drei Blitze über die Regie (ORIGIN KLICK)");
        int vor = r.gewitter.erdblitze;
        r.blitzTaste('T');
        Bolt t = r.gewitter.blitz.bolts().get(r.gewitter.blitz.bolts().size() - 1);
        check(r.gewitter.erdblitze == vor + 1 && Math.hypot(t.hitX - Site.LEUCHTTURM[0], t.hitZ - Site.LEUCHTTURM[1]) < 30,
                String.format(Locale.GERMAN, "Taste T: Einschlag %.0f m vom Leuchtturm%s", Math.hypot(t.hitX - Site.LEUCHTTURM[0], t.hitZ - Site.LEUCHTTURM[1]), t.attractor >= 0 ? " (in den Turm gefangen)" : ""));
        int k0 = r.gewitter.kronen;
        r.blitzTaste('K');
        check(r.gewitter.kronen == k0 + 1, "Taste K: eine Krone");
        r.gewitter.blitz.onThunder = null;
        try {
            java.lang.reflect.Method m = Renderer.class.getDeclaredMethod("donnerAngekommen", double.class);
            m.setAccessible(true);
            m.invoke(r, 400.0);
            java.lang.reflect.Field f = Renderer.class.getDeclaredField("beben");
            f.setAccessible(true);
            double b = f.getDouble(r);
            m.invoke(r, 5000.0);
            check(b > 0.6, String.format(Locale.GERMAN, "Beben bei Donner aus 400 m: %.2f", b));
        } catch (ReflectiveOperationException e) {
            check(false, "Beben: " + e);
        }
        Ansichten.setzen(data.scene().views());
        boolean gleich = Ansichten.ALLE.size() == 5;
        for (int i = 0; i < Math.min(5, Ansichten.ALLE.size()); i++) gleich &= Ansichten.ALLE.get(i).equals(Ansichten.VORGABE.get(i));
        check(gleich, "Ansichten aus den Daten = Vorgabe (5)");
        check(r.bauwerke.koerbe.equals(Lage.koerbe(Bauwerke.KORB_FARBEN)), "Strandkörbe aus den Daten = Vorgabe (4)");
        r.klang.dispose();
    }

    // ------------------------------------------------------------------ 5 Klang

    static void klang(ThunderData data) {
        say("Klang: Donner aus 1 km");
        Renderer r = new Renderer(new SceneClock());
        r.setDaten(data);
        r.setLevel(data.levels().get(1));
        r.gewitter.regieAn = false;
        r.camera.home();
        r.camera.snap();
        r.klang.offline = true;
        r.klang.feed(0.1);
        float[] ruhe = r.klang.s.render(1.0);
        Bolt b = r.gewitter.ausloesen(StrikeKind.CG, Site.BLICK, 1.0, 0);
        double t0 = r.gewitter.blitz.time();
        int d0 = r.klang.donner;
        double ankunft = -1;
        for (int i = 0; i < 300 && ankunft < 0; i++) {
            r.gewitter.blitz.step(1 / 30.0);
            if (r.klang.donner > d0) ankunft = r.gewitter.blitz.time() - t0;
        }
        double soll = Math.hypot(b.hitX, b.hitZ) / 343;
        check(ankunft > 0 && Math.abs(ankunft - soll) < 1.2, String.format(Locale.GERMAN, "Donner kommt nach %.1f s an (Schall aus %.0f m: %.1f s; der nächste Punkt des Kanals zählt)", ankunft, Math.hypot(b.hitX, b.hitZ), soll));
        float[] x = r.klang.s.render(3.0);
        double rq = rms(ruhe), rd = rms(x), peak = 0;
        for (float v : x) peak = Math.max(peak, Math.abs(v));
        check(rd > rq * 2 && peak < 1.0, String.format(Locale.GERMAN, "Donner hörbar: %.1f-mal so laut wie Wind und Brandung, Spitze %.2f", rd / Math.max(1e-9, rq), peak));
        r.klang.dispose();
    }

    static double rms(float[] x) {
        double s = 0;
        for (float v : x) s += v * v;
        return Math.sqrt(s / Math.max(1, x.length));
    }

    // ------------------------------------------------------------------ 6 Bauten

    static void bauten() {
        say("Bauten");
        Bauwerke b = new Bauwerke();
        check(b.koerbe.size() == 4, "vier Strandkörbe");
        check(b.pfaehle.size() >= 20, b.pfaehle.size() + " Pfähle der Seebrücke im Wasser (für den Klang)");
        check(b.laterneTurm[1] > Site.TURM_HOEHE, String.format(Locale.GERMAN, "Laterne des Leuchtturms %.1f m über dem Wasser", b.laterneTurm[1]));
    }

    // ------------------------------------------------------------------ 7 Datenbank

    static void datenbank() {
        DbConfig c = DbConfig.load();
        say("Datenbank " + c.describe());
        if (!c.enabled()) { say("  (db.enabled=false, übersprungen)"); return; }
        try (java.sql.Connection con = java.sql.DriverManager.getConnection(c.url(), c.user(), c.password())) {
            boolean d3 = OracleThunderRepository.hat3d(con);
            check(d3, "sql/04_th_3d.sql gelaufen (TH_SCENE_OBJECT.X_M, Z_M, ROT_DEG und TH_VIEW da)");
            if (!d3) return;
            try (var ps = con.prepareStatement("SELECT COUNT(*) FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'TH_STRIKE' AND COLUMN_NAME IN ('X_M','Z_M','BOLT_KIND')");
                 var rs = ps.executeQuery()) {
                rs.next();
                check(rs.getInt(1) == 3, "TH_STRIKE mit X_M, Z_M, BOLT_KIND");
            }
            ThunderData db = new OracleThunderRepository(c).load(c.scene()), ein = new BuiltinThunderRepository().load(c.scene());
            int gleich = 0;
            for (int i = 0; i < ein.scene().objects().size(); i++) {
                SceneObject a = db.scene().objects().get(i), b = ein.scene().objects().get(i);
                if (a.hatLage() && Math.abs(a.xM() - b.xM()) < 0.01 && Math.abs(a.zM() - b.zM()) < 0.01 && Math.abs(a.rotDeg() - b.rotDeg()) < 0.05) gleich++;
            }
            check(gleich == 7, "Lage der 7 Objekte in der Datenbank = Code (" + gleich + ")");
            check(db.scene().views().equals(ein.scene().views()), "5 Ansichten in TH_VIEW = Code (" + db.scene().views().size() + ")");
            try (var ps = con.prepareStatement("SELECT COUNT(*), COUNT(X_M) FROM TH_STRIKE"); var rs = ps.executeQuery()) {
                rs.next();
                say(String.format(Locale.GERMAN, "  Blitzprotokoll: %d Blitze, davon %d mit Ort in Metern", rs.getInt(1), rs.getInt(2)));
            }
        } catch (java.sql.SQLException e) {
            say("  Datenbank nicht erreichbar (" + e.getMessage().replaceAll("\\s+", " ").trim() + "): übersprungen");
        }
    }
}
