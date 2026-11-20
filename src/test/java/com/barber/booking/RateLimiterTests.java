package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RateLimiterTests {

    private static final long T = 1_000_000L;

    @Test
    void blocksAfterTheLimitAndReopensAfterTheWindow() {
        RateLimiter l = new RateLimiter(true);
        for (int i = 0; i < 3; i++) assertEquals(0, l.check("k", 3, 60_000, T + i));
        long wait = l.check("k", 3, 60_000, T + 10);
        assertTrue(wait >= 1 && wait <= 60, "wait=" + wait);
        assertEquals(0, l.check("k", 3, 60_000, T + 60_000));
    }

    @Test
    void retryAfterShrinksAsTimePasses() {
        RateLimiter l = new RateLimiter(true);
        assertEquals(0, l.check("k", 1, 60_000, T));
        assertEquals(30, l.check("k", 1, 60_000, T + 30_000));
        assertEquals(1, l.check("k", 1, 60_000, T + 59_999));
    }

    @Test
    void keysAreIndependent() {
        RateLimiter l = new RateLimiter(true);
        assertEquals(0, l.check("a", 1, 60_000, T));
        assertTrue(l.check("a", 1, 60_000, T + 1) > 0);
        assertEquals(0, l.check("b", 1, 60_000, T + 1));
    }

    @Test
    void disabledLimiterAllowsEverything() {
        RateLimiter l = new RateLimiter(false);
        for (int i = 0; i < 1000; i++) assertEquals(0, l.check("k", 1, 60_000, T));
    }
}
