package com.dan.thunder.model;

import java.awt.Color;

/**
 * Objekt einer Strandszene (TH_SCENE_OBJECT).
 *
 * @param x     Lage als Anteil der Bildbreite
 * @param depth Strand: 0 = Wasserkante, 1 = vorderer Bildrand
 * @param color Streifen- bzw. Bandfarbe, im Blitzlicht sichtbar; darf null sein
 * @param xM    seit Phase 18c: Lage in m (x Ost, Ursprung Hauptblick), null ohne {@code sql/04_th_3d.sql}
 * @param zM    Lage in m (z Süd)
 * @param rotDeg Richtung in Grad von Nord (Strandkorb: Öffnung; Seebrücke: Kurs zum Kopf)
 */
public record SceneObject(Type type, double x, double depth, double scale, Color color, Double xM, Double zM, Double rotDeg) {

    /** Ohne Lage in Metern (Daten vor Phase 18c). */
    public SceneObject(Type type, double x, double depth, double scale, Color color) { this(type, x, depth, scale, color, null, null, null); }

    public boolean hatLage() { return xM != null && zM != null; }

    public enum Type { STRANDKORB, LEUCHTTURM, SEEBRUECKE, DUENE }
}
