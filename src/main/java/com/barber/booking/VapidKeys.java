package com.barber.booking;

public record VapidKeys(String publicKey, String privateKey, String subject, boolean ephemeral) {

    @Override
    public String toString() {
        return "VapidKeys[gizli]";
    }
}
