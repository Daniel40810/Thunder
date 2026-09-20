package com.dan.thunder.model;

import java.util.List;

/** Strandszene (TH_SCENE) mit ihren Objekten. Positionen als Anteil von Bildbreite/-höhe. */
public record Scene(String code, String name, double horizonY, double shoreY, double emberX, double emberY,
                    List<SceneObject> objects) {

    public List<SceneObject> objectsOf(SceneObject.Type type) {
        return objects.stream().filter(o -> o.type() == type).toList();
    }
}
