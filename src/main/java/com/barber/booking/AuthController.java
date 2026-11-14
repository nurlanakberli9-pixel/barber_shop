package com.barber.booking;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class AuthController {

    record Me(String username, Long shopId, String shopName, String masterName) {}

    private final AppUserRepository users;

    public AuthController(AppUserRepository users) {
        this.users = users;
    }

    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void csrf(CsrfToken token) {
        token.getToken();
    }

    @GetMapping("/panel/me")
    @Transactional(readOnly = true)
    public Me me(Authentication auth) {
        AppUser u = users.findByUsername(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Barbershop b = u.getBarbershop();
        if (b == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return new Me(u.getUsername(), b.getId(), b.getName(), b.getMasterName());
    }
}
