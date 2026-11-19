package com.barber.booking;

public enum ServiceKind {
    HAIRCUT("Saç kəsimi", true),
    WASH("Saç yuma", false),
    BEARD("Saqqal kəsimi", true);

    private final String label;
    private final boolean levels;

    ServiceKind(String label, boolean levels) {
        this.label = label;
        this.levels = levels;
    }

    public String getLabel() { return label; }

    public boolean hasLevels() { return levels; }
}
