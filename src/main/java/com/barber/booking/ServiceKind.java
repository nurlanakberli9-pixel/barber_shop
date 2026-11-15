package com.barber.booking;

public enum ServiceKind {
    HAIRCUT("Saç kəsimi"),
    WASH("Saç yuma"),
    BEARD("Saqqal kəsimi");

    private final String label;

    ServiceKind(String label) {
        this.label = label;
    }

    public String getLabel() { return label; }
}
