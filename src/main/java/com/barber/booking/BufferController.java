package com.barber.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
@RequestMapping("/api/panel/buffers")
public class BufferController {

    record BufferDto(List<String> services, String label, int afterMinutes) {}
    record BufferIn(
            @NotNull @Size(min = 1, max = 3) List<@NotBlank String> services,
            @NotNull @Min(0) @Max(240) Integer afterMinutes) {}
    record BuffersRequest(@NotNull @Size(min = 7, max = 7) List<@NotNull @Valid BufferIn> buffers) {}

    private final AppUserRepository users;
    private final BarbershopRepository shops;
    private final ComboBufferRepository buffers;

    public BufferController(AppUserRepository users, BarbershopRepository shops,
            ComboBufferRepository buffers) {
        this.users = users;
        this.shops = shops;
        this.buffers = buffers;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<BufferDto> get(Authentication auth) {
        Long shopId = shopId(auth);
        Map<String, Integer> m = new HashMap<>();
        for (ComboBuffer b : buffers.findByBarbershopId(shopId)) {
            m.put(b.getComboKey(), b.getAfterMinutes());
        }
        return view(m);
    }

    @PutMapping
    @Transactional
    public List<BufferDto> put(@Valid @RequestBody BuffersRequest req, Authentication auth) {
        Barbershop shop = shops.findByIdForUpdate(shopId(auth))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Map<String, Integer> wanted = new LinkedHashMap<>();
        for (BufferIn b : req.buffers()) {
            if (b.afterMinutes() % 5 != 0) throw bad("Müddət 5-in qatı olmalıdır");
            Set<ServiceKind> kinds = EnumSet.noneOf(ServiceKind.class);
            for (String code : b.services()) {
                ServiceKind k;
                try {
                    k = ServiceKind.valueOf(code.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw bad("Naməlum xidmət");
                }
                if (!kinds.add(k)) throw bad("Eyni xidmət təkrarlanıb");
            }
            if (wanted.put(Combos.key(kinds), b.afterMinutes()) != null) {
                throw bad("Eyni kombinasiya təkrarlanıb");
            }
        }
        if (wanted.size() != 7) throw bad("Yeddi kombinasiyanın hamısı göndərilməlidir");
        buffers.deleteAll(buffers.findByBarbershopId(shop.getId()));
        buffers.flush();
        for (Map.Entry<String, Integer> e : wanted.entrySet()) {
            buffers.save(new ComboBuffer(shop, e.getKey(), e.getValue()));
        }
        return view(wanted);
    }

    private List<BufferDto> view(Map<String, Integer> m) {
        List<BufferDto> out = new ArrayList<>();
        for (List<ServiceKind> combo : Combos.all()) {
            out.add(new BufferDto(combo.stream().map(Enum::name).toList(),
                    Combos.label(combo), m.getOrDefault(Combos.key(combo), 0)));
        }
        return out;
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private Long shopId(Authentication auth) {
        AppUser u = users.findByUsername(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Barbershop b = u.getBarbershop();
        if (b == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return b.getId();
    }
}
