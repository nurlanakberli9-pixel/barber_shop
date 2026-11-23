package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ProdDatabaseGuardTests {

    @Test
    void h2AndMissingUrlsAreRejectedInProd() {
        assertThrows(IllegalStateException.class,
                () -> ProdDatabaseGuard.check("jdbc:h2:mem:barber;MODE=PostgreSQL"));
        assertThrows(IllegalStateException.class, () -> ProdDatabaseGuard.check(null));
        assertThrows(IllegalStateException.class, () -> ProdDatabaseGuard.check(""));
    }

    @Test
    void postgresUrlsAreAccepted() {
        assertDoesNotThrow(() -> ProdDatabaseGuard.check("jdbc:postgresql://db.example.com:5432/barber?sslmode=require"));
        assertDoesNotThrow(() -> ProdDatabaseGuard.check("jdbc:postgresql://localhost:5432/barber"));
    }
}
