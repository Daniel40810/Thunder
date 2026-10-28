import java.io.DataInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Prüfung von {@code lib/Renderer.jar} (Ant-Target {@code check-packages}, seit Phase 18a nach Sylt F30 A, wie in Sylt,
 * Brocken und Lighthouse). Die Element-Packages liegen nicht im Projekt Thunder: ihre Quelle ist das Projekt Renderer
 * (Paketvertrag Punkt 11), dort prüft {@code tools/PaketPruefung.java} jedes Package einzeln (Punkt 9). Hier wird nur
 * geprüft, ob das Jar alles hat, was Thunder braucht: jedes Package mit Klassen und README, Klassen für Java 21
 * (Version 65) oder älter, dazu die Brecher aus {@code surf} (mit {@code Surf.ostsee()}), die See aus {@code ocean} und
 * Himmel und Wolken. Aufruf ohne Ant: {@code java tools/JarPruefung.java lib/Renderer.jar}.
 */
public final class JarPruefung {
    /** Was Thunder aus Renderer.jar braucht (Phase 18a; lightning, rain, soundscape und beacon ab 18b). */
    static final String[] GEBRAUCHT = {"sky", "cloud", "ocean", "surf", "lightning", "rain", "soundscape", "beacon"};
    static final String[][] SHADER = {
            {"surf", "Surf.class", "WaveTrain.class", "Swash.class", "BreakerMesh.class", "gl/BreakerGl.class", "gl/breaker.vert", "gl/breaker.frag", "demo/SurfCheck.class"},
            {"ocean", "Ocean.class", "gl/OceanGl.class", "gl/surface.frag"},
            {"sky", "gl/SkyGl.class", "milkyway.png", "stars.bin"},
            {"cloud", "Clouds.class", "gl/CloudGl.class"}};

    public static void main(String[] a) throws Exception {
        File jar = new File(a.length > 0 ? a[0] : "lib/Renderer.jar");
        if (!jar.isFile()) { System.err.println("check-packages: " + jar + " fehlt"); System.exit(1); }
        Map<String, int[]> p = new TreeMap<>();   // Klassen, README, höchste Version
        try (ZipFile z = new ZipFile(jar)) {
            for (ZipEntry e : java.util.Collections.list(z.entries())) {
                String n = e.getName();
                if (!n.startsWith("com/dan/") || e.isDirectory()) continue;
                String[] t = n.split("/");
                if (t.length < 4) continue;
                int[] v = p.computeIfAbsent(t[2], k -> new int[3]);
                if (n.endsWith(".class")) {
                    v[0]++;
                    try (InputStream in = z.getInputStream(e); DataInputStream d = new DataInputStream(in)) {
                        d.readInt();
                        d.readUnsignedShort();
                        v[2] = Math.max(v[2], d.readUnsignedShort());
                    }
                } else if (t.length == 4 && t[3].equals("README.md")) v[1] = 1;
            }
            int fehler = 0;
            System.out.println("check-packages: " + jar.getName() + " mit " + p.size() + " Packages (Quelle: Projekt Renderer)");
            System.out.println("  Package        Klassen  README  Java");
            for (String g : GEBRAUCHT) {
                int[] v = p.get(g);
                boolean ok = v != null && v[0] > 0 && v[1] == 1 && v[2] <= 65;
                if (!ok) fehler++;
                System.out.printf("  %-14s %-8s %-7s %-5s %s%n", g, v == null ? "–" : v[0], v == null || v[1] == 0 ? "fehlt" : "ok", v == null ? "–" : v[2] - 44, ok ? "" : "FEHLER");
            }
            for (String[] s : SHADER)
                for (int i = 1; i < s.length; i++)
                    if (z.getEntry("com/dan/" + s[0] + "/" + s[i]) == null) { fehler++; System.out.println("  FEHLER: com/dan/" + s[0] + "/" + s[i] + " fehlt"); }
            if (fehler > 0) { System.out.println("check-packages: " + fehler + " Fehler – Renderer neu bauen (Clean and Build) und dist/Renderer.jar nach lib/ kopieren"); System.exit(1); }
            System.out.println("check-packages: alle " + GEBRAUCHT.length + " gebrauchten Packages in " + jar.getName()
                    + ", dazu die Brecher aus surf, die See, Himmel und Wolken.");
        }
    }
}
