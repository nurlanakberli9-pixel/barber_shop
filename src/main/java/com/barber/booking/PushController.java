package com.barber.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/panel/push")
public class PushController {

    private static final int MAX_PER_SHOP = 10;

    record KeyDto(String publicKey, boolean ephemeral) {}
    record SubscribeRequest(
            @NotBlank @Size(max = 600) String endpoint,
            @NotBlank @Size(max = 200) @Pattern(regexp = "^[A-Za-z0-9_=-]+$") String p256dh,
            @NotBlank @Size(max = 100) @Pattern(regexp = "^[A-Za-z0-9_=-]+$") String auth) {}
    record EndpointRequest(@NotBlank @Size(max = 600) String endpoint) {}
    record SentDto(int sent) {}

    private final AppUserRepository users;
    private final PushSubscriptionRepository subs;
    private final PushSender sender;
    private final VapidKeys keys;

    public PushController(AppUserRepository users, PushSubscriptionRepository subs,
            PushSender sender, VapidKeys keys) {
        this.users = users;
        this.subs = subs;
        this.sender = sender;
        this.keys = keys;
    }

    @GetMapping("/key")
    public KeyDto key() {
        return new KeyDto(keys.publicKey(), keys.ephemeral());
    }

    @PostMapping("/subscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void subscribe(@Valid @RequestBody SubscribeRequest req, Authentication auth) {
        Barbershop shop = myShop(auth);
        if (!PushEndpoints.allowed(req.endpoint())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bildiriş ünvanı tanınmır");
        }
        PushSubscription existing = subs.findByEndpoint(req.endpoint()).orElse(null);
        if (existing != null) {
            existing.update(shop, req.p256dh(), req.auth());
            return;
        }
        if (subs.countByBarbershopId(shop.getId()) >= MAX_PER_SHOP) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cihaz limiti dolub");
        }
        try {
            subs.saveAndFlush(new PushSubscription(shop, req.endpoint(), req.p256dh(), req.auth()));
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Yenidən cəhd edin");
        }
    }

    @PostMapping("/unsubscribe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void unsubscribe(@Valid @RequestBody EndpointRequest req, Authentication auth) {
        Barbershop shop = myShop(auth);
        subs.findByEndpoint(req.endpoint())
                .filter(s -> s.getBarbershop().getId().equals(shop.getId()))
                .ifPresent(subs::delete);
    }

    @PostMapping("/test")
    public SentDto test(Authentication auth) {
        Long shopId = users.findShopIdByUsername(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        if (subs.countByBarbershopId(shopId) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aktiv cihaz yoxdur");
        }
        int sent = sender.sendToShop(shopId, PushSender.json("Test bildirişi",
                "Bildirişlər işləyir", "test-" + System.nanoTime()));
        return new SentDto(sent);
    }

    private Barbershop myShop(Authentication auth) {
        AppUser u = users.findByUsername(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Barbershop b = u.getBarbershop();
        if (b == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return b;
    }
}
