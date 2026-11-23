package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ProdConfigGuardTests {

    private static VapidKeys keys(String subject, boolean ephemeral) {
        return new VapidKeys("pub", "priv", subject, ephemeral);
    }

    @Test
    void temporaryKeysAreRejectedInProd() {
        assertThrows(IllegalStateException.class,
                () -> ProdConfigGuard.check(keys("mailto:owner@mydomain.tld", true)));
    }

    @Test
    void placeholderOrMalformedSubjectsAreRejected() {
        for (String subject : new String[] {"mailto:admin@example.com", "http://mydomain.tld",
                "owner@mydomain.tld", "", null}) {
            assertThrows(IllegalStateException.class,
                    () -> ProdConfigGuard.check(keys(subject, false)), String.valueOf(subject));
        }
    }

    @Test
    void stableKeysWithARealSubjectAreAccepted() {
        assertDoesNotThrow(() -> ProdConfigGuard.check(keys("mailto:owner@mydomain.tld", false)));
        assertDoesNotThrow(() -> ProdConfigGuard.check(keys("https://mydomain.tld/contact", false)));
    }
}
