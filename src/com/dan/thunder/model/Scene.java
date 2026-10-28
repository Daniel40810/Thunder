package com.dan.thunder.model;

import java.util.List;

/** Strandszene (TH_SCENE) mit ihren Objekten. Positionen als Anteil von Bildbreite/-höhe. */
public record Scene(String code, String name, double horizonY, double shoreY, double emberX, double emberY,
                    List<SceneObject> objects, List<ViewDef> views) {

    /** Ohne Ansichten (Daten vor Phase 18c): die App nimmt ihre eingebauten. */
    public Scene(String code, String name, double horizonY, double shoreY, double emberX, double emberY, List<SceneObject> objects) {
        this(code, name, horizonY, shoreY, emberX, emberY, objects, List.of());
    }

    public List<SceneObject> objectsOf(SceneObject.Type type) {
        return objects.stream().filter(o -> o.type() == type).toList();
    }
}
