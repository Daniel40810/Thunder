package com.dan.thunder.model;

/** Tageszeit der Szene: Abendgewitter mit Resthelligkeit (Thunder1.jpg) oder Nacht (Thunder.jpg). */
public enum Mood {
    ABEND("Abend"),
    NACHT("Nacht");

    private final String label;

    Mood(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
