package com.dan.thunder.storm;

/**
 * Ein Ast eines Blitzes als Polylinie in Bildpunkten.
 *
 * @param width relative Stärke (Hauptkanal 1)
 * @param start ab welchem Anteil des Leitblitz-Fortschritts dieser Ast zu wachsen beginnt
 * @param span  wie viel Fortschritt der Ast bis zur vollen Länge braucht
 */
public record BoltSegment(float[] xs, float[] ys, float width, float start, float span, boolean main) {

    public int size() {
        return xs.length;
    }
}
