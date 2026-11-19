package com.barber.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/panel/services")
public class ServiceController {

    record ServiceDto(String code, String name, boolean levels, Integer minutes,
            Integer low, Integer medium, Integer high) {}
    record ServicesResponse(List<ServiceDto> services) {}
    record ServiceUpdate(
            @NotBlank String code,
            @Min(5) @Max(240) Integer minutes,
            @Min(5) @Max(240) Integer low,
            @Min(5) @Max(240) Integer medium,
            @Min(5) @Max(240) Integer high) {}
    record ServicesRequest(
            @NotNull @Size(min = 3, max = 3) List<@NotNull @Valid ServiceUpdate> services) {}

    private final AppUserRepository users;
    private final BarbershopRepository shops;
    private final ServiceTypeRepository types;

    public ServiceController(AppUserRepository users, BarbershopRepository shops,
            ServiceTypeRepository types) {
        this.users = users;
        this.shops = shops;
        this.types = types;
    }

    @GetMapping
    @Transactional
    public ServicesResponse list(Authentication auth) {
        return respond(ensureDefaults(lockedShop(auth)));
    }

    @PutMapping
    @Transactional
    public ServicesResponse update(@Valid @RequestBody ServicesRequest req, Authentication auth) {
        Barbershop shop = lockedShop(auth);
        Map<ServiceKind, ServiceUpdate> wanted = new EnumMap<>(ServiceKind.class);
        for (ServiceUpdate u : req.services()) {
            ServiceKind kind = parse(u.code());
            validate(kind, u);
            if (wanted.put(kind, u) != null) throw bad("Eyni xidmət təkrarlanıb");
        }
        if (wanted.size() != ServiceKind.values().length) {
            throw bad("Üç xidmətin hamısı göndərilməlidir");
        }
        List<ServiceType> list = ensureDefaults(shop);
        for (ServiceType s : list) {
            ServiceUpdate u = wanted.get(s.getKind());
            if (s.getKind().hasLevels()) {
                s.setLevels(u.low(), u.medium(), u.high());
                s.setSingle(null);
            } else {
                s.setSingle(u.minutes());
                s.setLevels(null, null, null);
            }
        }
        return respond(list);
    }

    private static void validate(ServiceKind kind, ServiceUpdate u) {
        if (kind.hasLevels()) {
            if (u.low() == null || u.medium() == null || u.high() == null) {
                throw bad("Az, orta və çox üçün müddət yazılmalıdır");
            }
            for (int v : new int[] {u.low(), u.medium(), u.high()}) {
                if (v % 5 != 0) throw bad("Müddət 5-in qatı olmalıdır");
            }
            if (u.low() > u.medium() || u.medium() > u.high()) {
                throw bad("Müddət az ≤ orta ≤ çox olmalıdır");
            }
        } else {
            if (u.minutes() == null) throw bad("Müddət yazılmalıdır");
            if (u.minutes() % 5 != 0) throw bad("Müddət 5-in qatı olmalıdır");
        }
    }

    private List<ServiceType> ensureDefaults(Barbershop shop) {
        List<ServiceType> existing = types.findByBarbershopIdOrderById(shop.getId());
        Set<ServiceKind> have = existing.stream().map(ServiceType::getKind)
                .collect(Collectors.toSet());
        List<ServiceType> all = new ArrayList<>(existing);
        for (ServiceKind k : ServiceKind.values()) {
            if (!have.contains(k)) all.add(types.save(new ServiceType(shop, k)));
        }
        return all;
    }

    private ServicesResponse respond(List<ServiceType> list) {
        return new ServicesResponse(list.stream()
                .sorted(Comparator.comparingInt((ServiceType s) -> s.getKind().ordinal()))
                .map(s -> new ServiceDto(s.getKind().name(), s.getKind().getLabel(),
                        s.getKind().hasLevels(), s.getSingleMinutes(), s.getLowMinutes(),
                        s.getMediumMinutes(), s.getHighMinutes()))
                .toList());
    }

    private static ServiceKind parse(String code) {
        try {
            return ServiceKind.valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw bad("Naməlum xidmət");
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private Barbershop lockedShop(Authentication auth) {
        AppUser u = users.findByUsername(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Barbershop b = u.getBarbershop();
        if (b == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return shops.findByIdForUpdate(b.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
