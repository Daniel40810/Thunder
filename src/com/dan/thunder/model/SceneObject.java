package com.dan.thunder.model;

import java.awt.Color;

/**
 * Objekt einer Strandszene (TH_SCENE_OBJECT).
 *
 * @param x     Lage als Anteil der Bildbreite
 * @param depth Strand: 0 = Wasserkante, 1 = vorderer Bildrand
 * @param color Streifen- bzw. Bandfarbe, im Blitzlicht sichtbar; darf null sein
 */
public record SceneObject(Type type, double x, double depth, double scale, Color color) {

    public enum Type { STRANDKORB, LEUCHTTURM, SEEBRUECKE, DUENE }
}
