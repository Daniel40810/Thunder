package com.dan.thunder;

import com.dan.thunder.data.BuiltinThunderRepository;
import com.dan.thunder.model.Mood;
import com.dan.thunder.model.Palette;
import com.dan.thunder.model.PaletteRole;
import com.dan.thunder.model.SceneObject;
import com.dan.thunder.model.StormLevel;
import com.dan.thunder.model.ThunderData;
import com.dan.thunder.storm.BoltSegment;
import com.dan.thunder.storm.Envelope;
import com.dan.thunder.storm.Stage;
import com.dan.thunder.storm.StormDirector;
import com.dan.thunder.storm.Strike;
import com.dan.thunder.storm.StrikeKind;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prüft ohne Oberfläche und ohne Datenbank:
 * eingebaute Daten = sql/02_th_data.sql, Blitzform reproduzierbar per Seed, Lichtkurve und Regie plausibel.
 * Start aus dem Projektordner: {@code java -cp build/classes:build/test:lib/* com.dan.thunder.ThunderSmokeTest}
 */
public final class ThunderSmokeTest {

    private static int failures;

    public static void main(String[] args) throws Exception {
        ThunderData data = new BuiltinThunderRepository().load("OSTSEE");
        String sql = Files.readString(Path.of(args.length > 0 ? args[0] : "sql/02_th_data.sql"), StandardCharsets.UTF_8);

        // --- Stufen wie im SQL
        Matcher m = Pattern.compile("VALUES \\('(\\w+)',\\s*'([^']+)',\\s*(\\d+),\\s*([\\d.]+),\\s*([\\d.]+),\\s*([\\d.]+),\\s*([\\d.]+),\\s*([\\d.]+),\\s*(\\d+),\\s*([\\d.]+),\\s*([\\d.]+)").matcher(sql);
        int levels = 0;
        while (m.find()) {
            levels++;
            StormLevel l = data.level(m.group(1)).orElse(null);
            check(l != null, "Stufe " + m.group(1) + " eingebaut");
            if (l == null) {
                continue;
            }
            check(l.name().equals(m.group(2)), "Name " + l.code());
            check(l.id() == Integer.parseInt(m.group(3)), "Reihenfolge/Id " + l.code());
            checkNum(l.rateSec(), m.group(4), "RATE_SEC " + l.code());
            checkNum(l.shareIc(), m.group(5), "SHARE_IC " + l.code());
            checkNum(l.shareCrown(), m.group(6), "SHARE_CROWN " + l.code());
            checkNum(l.distMinKm(), m.group(7), "DIST_MIN " + l.code());
            checkNum(l.distMaxKm(), m.group(8), "DIST_MAX " + l.code());
            check(l.rainDrops() == Integer.parseInt(m.group(9)), "RAIN " + l.code());
            checkNum(l.wind(), m.group(10), "WIND " + l.code());
            checkNum(l.ember(), m.group(11), "EMBER " + l.code());
            check(l.shareIc() + l.shareCrown() <= 1, "Anteile ≤ 1 " + l.code());
        }
        check(levels == data.levels().size() && levels == 3, "3 Stufen im SQL und eingebaut");

        // --- Farben wie im SQL, jede Rolle in beiden Stimmungen
        Matcher p = Pattern.compile("VALUES \\('(ABEND|NACHT)', '(\\w+)',\\s*'(#[0-9A-Fa-f]{6})'\\)").matcher(sql);
        int colors = 0;
        while (p.find()) {
            colors++;
            Palette pal = data.palette(Mood.valueOf(p.group(1)));
            PaletteRole role = PaletteRole.valueOf(p.group(2));
            check(pal.get(role).equals(Palette.hex(p.group(3))), "Farbe " + p.group(1) + "/" + role);
        }
        check(colors == PaletteRole.values().length * 2, "Farben: " + colors + " = 2 × " + PaletteRole.values().length);

        // --- Szenenobjekte wie im SQL
        Matcher o = Pattern.compile("SELECT SCENE_ID, '(\\w+)',\\s*([\\d.]+),\\s*([\\d.]+),\\s*([\\d.]+),\\s*(NULL|'#[0-9A-Fa-f]{6}'),\\s*(\\d+)").matcher(sql);
        List<SceneObject> objs = data.scene().objects();
        int i = 0;
        while (o.find()) {
            check(i < objs.size(), "Objekt " + (i + 1) + " eingebaut");
            if (i < objs.size()) {
                SceneObject so = objs.get(i);
                check(so.type().name().equals(o.group(1)), "Objekttyp " + (i + 1));
                checkNum(so.x(), o.group(2), "POS_X " + (i + 1));
                checkNum(so.depth(), o.group(3), "DEPTH " + (i + 1));
                checkNum(so.scale(), o.group(4), "SCALE " + (i + 1));
                String hex = o.group(5).equals("NULL") ? null : o.group(5).replace("'", "");
                check(hex == null ? so.color() == null : Palette.hex(hex).equals(so.color()), "Farbe Objekt " + (i + 1));
            }
            i++;
        }
        check(i == objs.size() && i == 7, "7 Szenenobjekte");

        // --- Blitzform: gleicher Seed → gleicher Blitz
        Stage stage = new Stage() {
            public double width() { return 1440; }
            public double height() { return 820; }
            public double horizonY() { return 492; }
            public double shoreY(double x) { return 656; }
            public double emberX() { return 0.62; }
            public double emberY() { return 0.33; }
        };
        StormDirector d = new StormDirector(data.level("STORM").orElseThrow());
        Strike a = d.spawn(StrikeKind.CG, 0, stage, Double.NaN, Double.NaN, false, 4711L);
        Strike b = d.spawn(StrikeKind.CG, 0, stage, Double.NaN, Double.NaN, false, 4711L);
        BoltSegment ma = a.bolts().get(0).get(0), mb = b.bolts().get(0).get(0);
        check(a.bolts().get(0).size() == b.bolts().get(0).size() && java.util.Arrays.equals(ma.xs(), mb.xs())
                && java.util.Arrays.equals(ma.ys(), mb.ys()), "Seed 4711 ergibt zweimal denselben Blitz");
        check(Math.abs(ma.xs()[ma.size() - 1] - a.gx()) < 1e-3 && Math.abs(ma.ys()[ma.size() - 1] - a.gy()) < 1e-3,
                "Hauptkanal endet am Einschlag");
        check(a.gy() > stage.horizonY() && a.gy() < 656, "Einschlag liegt im Wasser");

        // --- Entfernung und Donner
        Random r = new Random(1);
        for (StormLevel l : data.levels()) {
            d.setLevel(l, 0);
            for (int k = 0; k < 200; k++) {
                Strike s = d.spawn(StrikeKind.values()[k % 3], 0, stage, Double.NaN, Double.NaN, false, r.nextLong());
                check(s.distKm() >= l.distMinKm() - 1e-9 && s.distKm() <= l.distMaxKm() + 1e-9, "Entfernung in " + l.code());
                check(Math.abs(s.thunderDelayMs() - s.distKm() * 2920) < 1e-6, "Donner 2,92 s/km");
                check(s.kind() == StrikeKind.IC ? s.peakKa() == null : s.peakKa() != null, "kA nur bei Erdblitzen");
            }
        }
        Strike near = d.strikeAt(700, 10, 0, stage);
        Strike far = d.strikeAt(700, 480, 0, stage);
        check(near.distKm() < far.distKm(), "Klick weiter oben = näherer Blitz");

        // --- Lichtkurve
        Envelope e = Envelope.random(new Random(3), 100, 3, 1, false);
        check(e.intensity(50) < 0.1, "Leitblitz schwach");
        check(e.intensity(100) > 0.9, "Hauptentladung hell");
        check(e.intensity(e.durationMs()) < 0.05, "am Ende erloschen");
        check(e.strokeCount() == 3, "3 Entladungen");
        Envelope soft = Envelope.random(new Random(3), 100, 3, 1, true);
        check(soft.strokeCount() == 1 && soft.intensity(100) <= 0.65, "sanft: eine gedämpfte Entladung");

        // --- Ton (ohne Audiogerät gerechnet)
        double lastPeak = 2;
        for (double dist : new double[] { 0.5, 3, 10 }) {
            com.dan.thunder.audio.ThunderSynth synth = new com.dan.thunder.audio.ThunderSynth();
            synth.setRain(0.8);
            synth.setSurf(0.6);
            float[] x = synth.renderOffline(8, dist);
            double peak = 0, head = 0, tail = 0;
            for (int k = 0; k < x.length; k++) {
                peak = Math.max(peak, Math.abs(x[k]));
                if (k < 44_100) {
                    head += x[k] * x[k];
                } else if (k > 44_100 * 7) {
                    tail += x[k] * x[k];
                }
            }
            check(peak < 0.95, "Donner " + dist + " km übersteuert nicht");
            check(peak < lastPeak, "Donner " + dist + " km leiser als näherer");
            check(head > tail * 2, "Donner " + dist + " km hörbar über Regen und Brandung");
            lastPeak = peak;
        }

        System.out.println(failures == 0 ? "ThunderSmokeTest: alles in Ordnung" : "ThunderSmokeTest: " + failures + " Fehler");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void checkNum(double v, String expected, String what) {
        check(Math.abs(v - Double.parseDouble(expected)) < 1e-9, what + " (" + String.format(Locale.ROOT, "%s", v) + " ≠ " + expected + ")");
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            failures++;
            System.out.println("FEHLER: " + what);
        }
    }
}
