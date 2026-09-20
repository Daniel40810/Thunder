package com.dan.thunder.storm;

/** Blitzarten. {@link #dbCode()} entspricht TH_STRIKE.KIND. */
public enum StrikeKind {
    CG("CG", "Wolke – Erde"),
    IC("IC", "In der Wolke"),
    CROWN("KRONE", "Blitzkrone");

    private final String dbCode;
    private final String label;

    StrikeKind(String dbCode, String label) {
        this.dbCode = dbCode;
        this.label = label;
    }

    public String dbCode() {
        return dbCode;
    }

    public String label() {
        return label;
    }
}
