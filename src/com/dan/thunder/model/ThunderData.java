package com.dan.thunder.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Alles, was die Szene aus der Datenbank (oder dem Rückfall) braucht. */
public record ThunderData(List<StormLevel> levels, Scene scene, Map<Mood, Palette> palettes, String source) {

    public Optional<StormLevel> level(String code) {
        return levels.stream().filter(l -> l.code().equalsIgnoreCase(code)).findFirst();
    }

    public Palette palette(Mood mood) {
        return palettes.get(mood);
    }
}
