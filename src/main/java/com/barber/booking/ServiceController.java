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
import org.springframework.dao.DataIntegrityViolationException;
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

    record ServiceDto(String code, String name, Integer durationMinutes) {}
    record ServicesResponse(List<ServiceDto> services, Integer minimumWindowMinutes) {}
    record ServiceUpdate(
            @NotBlank String code,
            @NotNull @Min(5) @Max(240) Integer durationMinutes) {}
    record ServicesRequest(
            @NotNull @Size(min = 3, max = 3) List<@NotNull @Valid ServiceUpdate> services) {}

    private final AppUserRepository users;
    private final ServiceTypeRepository types;

    public ServiceController(AppUserRepository users, ServiceTypeRepository types) {
        this.users = users;
        this.types = types;
    }

    @GetMapping
    @Transactional
    public ServicesResponse list(Authentication auth) {
        return respond(ensureDefaults(myShop(auth)));
    }

    @PutMapping
    @Transactional
    public ServicesResponse update(@Valid @RequestBody ServicesRequest req, Authentication auth) {
        Barbershop shop = myShop(auth);
        Map<ServiceKind, Integer> wanted = new EnumMap<>(ServiceKind.class);
        for (ServiceUpdate u : req.services()) {
            ServiceKind kind = parse(u.code());
            if (u.durationMinutes() % 5 != 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Müddət 5-in qatı olmalıdır");
            }
            if (wanted.put(kind, u.durationMinutes()) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Eyni xidmət təkrarlanıb");
            }
        }
        if (wanted.size() != ServiceKind.values().length) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Üç xidmətin hamısı göndərilməlidir");
        }
        List<ServiceType> list = ensureDefaults(shop);
        for (ServiceType s : list) {
            s.setDurationMinutes(wanted.get(s.getKind()));
        }
        return respond(list);
    }

    private List<ServiceType> ensureDefaults(Barbershop shop) {
        List<ServiceType> existing = types.findByBarbershopIdOrderById(shop.getId());
        if (existing.size() == ServiceKind.values().length) return existing;
        Set<ServiceKind> have = existing.stream().map(ServiceType::getKind)
                .collect(Collectors.toSet());
        List<ServiceType> all = new ArrayList<>(existing);
        try {
            for (ServiceKind k : ServiceKind.values()) {
                if (!have.contains(k)) all.add(types.save(new ServiceType(shop, k)));
            }
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Eyni anda başqa sorğu getdi, yenidən cəhd edin");
        }
        return all;
    }

    private ServicesResponse respond(List<ServiceType> list) {
        List<ServiceType> sorted = list.stream()
                .sorted(Comparator.comparingInt((ServiceType s) -> s.getKind().ordinal()))
                .toList();
        List<ServiceDto> dtos = sorted.stream()
                .map(s -> new ServiceDto(s.getKind().name(), s.getKind().getLabel(),
                        s.getDurationMinutes()))
                .toList();
        boolean complete = sorted.stream().allMatch(s -> s.getDurationMinutes() != null);
        Integer minimum = complete
                ? sorted.stream().mapToInt(ServiceType::getDurationMinutes).sum()
                : null;
        return new ServicesResponse(dtos, minimum);
    }

    private static ServiceKind parse(String code) {
        try {
            return ServiceKind.valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Naməlum xidmət");
        }
    }

    private Barbershop myShop(Authentication auth) {
        AppUser u = users.findByUsername(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Barbershop b = u.getBarbershop();
        if (b == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return b;
    }
}
