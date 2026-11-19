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
        var barbers = users.findAll().stream().filter(u -> "BARBER".equals(u.getRole())).toList();
        assertEquals(8, barbers.size());
        for (AppUser u : barbers) {
            assertTrue(u.getPasswordHash().startsWith("$2"), "BCrypt hash olmalıdır");
            assertFalse(encoder.matches("password", u.getPasswordHash()));
            assertTrue(u.isEnabled());
        }
    }

    @Test
    void usernamesAreUnique() {
        assertTrue(users.findByUsername("mia").isPresent());
        assertTrue(users.findByUsername("nobody").isEmpty());
    }
}
