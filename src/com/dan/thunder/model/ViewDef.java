package com.dan.thunder.model;

/**
 * Eine Ansicht (TH_VIEW, seit Phase 18c): Taste, Name, Auge (x Ost, z Süd in m), Augenhöhe über dem Bezug (GRUND oder
 * DECK der Seebrücke), Blickrichtung (Grad von Nord), Neigung (Grad, positiv nach unten), Öffnungswinkel senkrecht.
 */
public record ViewDef(int key, String name, double xM, double zM, double aboveM, String reference, double headingDeg,
                      double pitchDeg, double fovyDeg) {

    public boolean onDeck() { return "DECK".equals(reference); }
}
