package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class AccountSeedTests {

    @Autowired
    AppUserRepository users;
    @Autowired
    PasswordEncoder encoder;

    @Test
    void everyBarbershopHasOneAccountWithHashedPassword() {
        var all = users.findAll();
        assertEquals(8, all.size());
        for (AppUser u : all) {
            assertTrue(u.getPasswordHash().startsWith("$2"), "BCrypt hash olmalıdır");
            assertFalse(encoder.matches("password", u.getPasswordHash()));
            assertEquals("BARBER", u.getRole());
        }
    }

    @Test
    void usernamesAreUnique() {
        assertTrue(users.findByUsername("mia").isPresent());
        assertTrue(users.findByUsername("nobody").isEmpty());
    }
}
