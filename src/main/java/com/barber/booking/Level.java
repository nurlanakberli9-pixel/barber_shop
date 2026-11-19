package com.barber.booking;

public enum Level {
    LOW("Az"), MEDIUM("Orta"), HIGH("Çox");

    private final String word;

    Level(String word) {
        this.word = word;
    }

    public String label(ServiceKind kind) {
        return word + (kind == ServiceKind.BEARD ? " saqqal" : " saç");
    }
}
