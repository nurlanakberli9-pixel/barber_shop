package com.barber.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/panel")
public class PanelController {

    record PanelSlot(Long id, LocalDateTime startTime, String status,
            String customerName, String customerPhone) {}
    record CreateSlotsRequest(
            @NotNull LocalDate date,
            @Min(9) @Max(20) int fromHour,
            @Min(10) @Max(21) int toHour) {}
    record CreateResult(int created, int skipped) {}

    private final AppUserRepository users;
    private final SlotRepository slots;

    public PanelController(AppUserRepository users, SlotRepository slots) {
        this.users = users;
        this.slots = slots;
    }

    @GetMapping("/day")
    @Transactional
    public List<PanelSlot> day(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication auth) {
        Barbershop shop = myShop(auth);
        LocalDateTime now = LocalDateTime.now();
        return slots.findByBarbershopIdAndStartTimeBetweenOrderByStartTime(
                        shop.getId(), date.atStartOfDay(), date.plusDays(1).atStartOfDay())
                .stream()
                .map(s -> s.isBooked()
                        ? new PanelSlot(s.getId(), s.getStartTime(), "BOOKED",
                                s.getCustomerName(), s.getCustomerPhone())
                        : new PanelSlot(s.getId(), s.getStartTime(),
                                s.getStartTime().isBefore(now) ? "PAST" : "FREE", null, null))
                .toList();
    }

    @PostMapping("/slots")
    @Transactional
    public CreateResult create(@Valid @RequestBody CreateSlotsRequest req, Authentication auth) {
        Barbershop shop = myShop(auth);
        LocalDate today = LocalDate.now();
        if (req.date().isBefore(today) || req.date().isAfter(today.plusDays(59))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tarix bugündən 60 günlük aralıqda olmalıdır");
        }
        if (req.toHour() <= req.fromHour()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Bitmə saatı başlama saatından sonra olmalıdır");
        }
        LocalDateTime now = LocalDateTime.now();
        Set<LocalDateTime> existing = slots
                .findByBarbershopIdAndStartTimeBetweenOrderByStartTime(shop.getId(),
                        req.date().atStartOfDay(), req.date().plusDays(1).atStartOfDay())
                .stream().map(Slot::getStartTime).collect(Collectors.toSet());
        List<Slot> toSave = new ArrayList<>();
        int skipped = 0;
        for (int h = req.fromHour(); h < req.toHour(); h++) {
            LocalDateTime t = req.date().atTime(h, 0);
            if (t.isBefore(now) || existing.contains(t)) {
                skipped++;
            } else {
                toSave.add(new Slot(shop, t));
            }
        }
        try {
            slots.saveAll(toSave);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Saatlar eyni anda başqa sorğu ilə yaradılıb, yenidən cəhd edin");
        }
        return new CreateResult(toSave.size(), skipped);
    }

    @DeleteMapping("/slots/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Long id, Authentication auth) {
        Slot slot = mySlot(id, auth);
        if (slot.isBooked()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Dolu saatı silmək olmaz, əvvəl rezervi ləğv edin");
        }
        slots.delete(slot);
    }

    @PostMapping("/slots/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void cancel(@PathVariable Long id, Authentication auth) {
        Slot slot = mySlot(id, auth);
        if (!slot.isBooked()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu saat rezerv olunmayıb");
        }
        slot.cancelBooking();
    }

    private Barbershop myShop(Authentication auth) {
        AppUser u = users.findByUsername(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Barbershop b = u.getBarbershop();
        if (b == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return b;
    }

    private Slot mySlot(Long id, Authentication auth) {
        Barbershop shop = myShop(auth);
        return slots.findByIdAndBarbershopId(id, shop.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
