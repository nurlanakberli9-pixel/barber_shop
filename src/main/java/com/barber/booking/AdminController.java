package com.barber.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    record Me(String username) {}
    record BarberRow(Long id, String username, String shopName, String masterName,
            String address, boolean enabled) {}
    record CreateBarber(
            @NotBlank @Pattern(regexp = "^[a-z0-9._-]{3,32}$") String username,
            @NotBlank @Size(min = 10, max = 72) String password,
            @NotBlank @Size(max = 80) String shopName,
            @NotBlank @Size(max = 60) String masterName,
            @NotBlank @Size(max = 120) String address) {}
    record PasswordReset(@NotBlank @Size(min = 10, max = 72) String password) {}
    record EnabledRequest(@NotNull Boolean enabled) {}

    private final AppUserRepository users;
    private final BarbershopRepository shops;
    private final PasswordEncoder encoder;

    public AdminController(AppUserRepository users, BarbershopRepository shops,
            PasswordEncoder encoder) {
        this.users = users;
        this.shops = shops;
        this.encoder = encoder;
    }

    @GetMapping("/me")
    public Me me(Authentication auth) {
        return new Me(auth.getName());
    }

    @GetMapping("/barbers")
    @Transactional(readOnly = true)
    public List<BarberRow> list() {
        return users.findByRoleOrderById("BARBER").stream()
                .filter(u -> u.getBarbershop() != null)
                .map(AdminController::row)
                .toList();
    }

    @PostMapping("/barbers")
    @Transactional
    public BarberRow create(@Valid @RequestBody CreateBarber req) {
        checkPassword(req.password());
        if (users.findByUsername(req.username()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu istifadəçi adı artıq var");
        }
        Barbershop shop = shops.save(new Barbershop(req.shopName().trim(),
                req.masterName().trim(), req.address().trim(), 0.0));
        try {
            AppUser u = users.saveAndFlush(new AppUser(req.username(),
                    encoder.encode(req.password()), "BARBER", shop));
            return row(u);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu istifadəçi adı artıq var");
        }
    }

    @PostMapping("/barbers/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordReset req) {
        checkPassword(req.password());
        barber(id).setPasswordHash(encoder.encode(req.password()));
    }

    @PostMapping("/barbers/{id}/enabled")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void setEnabled(@PathVariable Long id, @Valid @RequestBody EnabledRequest req) {
        AppUser u = barber(id);
        u.setEnabled(req.enabled());
        u.getBarbershop().setActive(req.enabled());
    }

    private AppUser barber(Long id) {
        return users.findById(id)
                .filter(u -> "BARBER".equals(u.getRole()) && u.getBarbershop() != null)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static BarberRow row(AppUser u) {
        Barbershop b = u.getBarbershop();
        return new BarberRow(u.getId(), u.getUsername(), b.getName(), b.getMasterName(),
                b.getAddress(), u.isEnabled());
    }

    private static void checkPassword(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parol çox uzundur");
        }
    }
}
