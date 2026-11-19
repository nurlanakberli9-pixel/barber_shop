package com.barber.booking;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final Pattern NAME = Pattern.compile("^[a-z0-9._-]{3,32}$");

    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public AdminBootstrap(AppUserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        String password = System.getenv("ADMIN_PASSWORD");
        if (password == null || password.isBlank()) {
            log.info("ADMIN_PASSWORD verilməyib, admin hesabı yaradılmadı.");
            return;
        }
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (password.length() < 10 || bytes > 72) {
            log.error("ADMIN_PASSWORD 10-72 simvol olmalıdır, admin hesabı yaradılmadı.");
            return;
        }
        String username = System.getenv("ADMIN_USERNAME");
        if (username == null || username.isBlank()) username = "admin";
        if (!NAME.matcher(username).matches()) {
            log.error("ADMIN_USERNAME düzgün deyil, admin hesabı yaradılmadı.");
            return;
        }
        if (users.findByUsername(username).isPresent()) {
            log.info("Admin hesabı artıq var: {}", username);
            return;
        }
        users.save(new AppUser(username, encoder.encode(password), "ADMIN", null));
        log.warn("Admin hesabı yaradıldı: {}", username);
    }
}
