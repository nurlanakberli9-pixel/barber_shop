package com.barber.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class BookingController {

    record ShopDto(Long id, String name, String masterName, String address, double rating) {}
    record SlotDto(Long id, LocalDateTime startTime) {}
    record DaySlotDto(Long id, LocalDateTime startTime, String status) {}
    record DayStat(LocalDate date, int total, int free) {}
    record BookRequest(
            @NotBlank String name,
            @NotBlank @Pattern(regexp = "^\\+?[0-9]{9,15}$") String phone) {}

    private final BarbershopRepository shops;
    private final SlotRepository slots;

    public BookingController(BarbershopRepository shops, SlotRepository slots) {
        this.shops = shops;
        this.slots = slots;
    }

    @GetMapping("/shops")
    public List<ShopDto> listShops() {
        return shops.findAll().stream()
                .map(s -> new ShopDto(s.getId(), s.getName(), s.getMasterName(),
                        s.getAddress(), s.getRating()))
                .toList();
    }

    @GetMapping("/shops/{id}/slots")
    public List<SlotDto> freeSlots(@PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return slots.findByBarbershopIdAndBookedFalseAndStartTimeBetweenOrderByStartTime(
                        id, date.atStartOfDay(), date.plusDays(1).atStartOfDay()).stream()
                .map(s -> new SlotDto(s.getId(), s.getStartTime()))
                .toList();
    }

    @GetMapping("/shops/{id}/day")
    public List<DaySlotDto> day(@PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        requireShop(id);
        LocalDateTime now = LocalDateTime.now();
        return slots.findByBarbershopIdAndStartTimeBetweenOrderByStartTime(
                        id, date.atStartOfDay(), date.plusDays(1).atStartOfDay()).stream()
                .map(s -> new DaySlotDto(s.getId(), s.getStartTime(),
                        s.isBooked() ? "BOOKED"
                                : s.getStartTime().isBefore(now) ? "PAST" : "FREE"))
                .toList();
    }

    @GetMapping("/shops/{id}/availability")
    public List<DayStat> availability(@PathVariable Long id,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(defaultValue = "60") int days) {
        requireShop(id);
        LocalDate start = from != null ? from : LocalDate.now();
        int n = Math.min(Math.max(days, 1), 62);
        LocalDateTime now = LocalDateTime.now();
        Map<LocalDate, int[]> counts = new TreeMap<>();
        for (int i = 0; i < n; i++) counts.put(start.plusDays(i), new int[2]);
        for (Slot s : slots.findByBarbershopIdAndStartTimeBetweenOrderByStartTime(
                id, start.atStartOfDay(), start.plusDays(n).atStartOfDay())) {
            int[] c = counts.get(s.getStartTime().toLocalDate());
            if (c == null) continue;
            c[0]++;
            if (!s.isBooked() && s.getStartTime().isAfter(now)) c[1]++;
        }
        return counts.entrySet().stream()
                .map(e -> new DayStat(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
    }

    @PostMapping("/slots/{id}/book")
    @Transactional
    public SlotDto book(@PathVariable Long id, @Valid @RequestBody BookRequest req) {
        Slot slot = slots.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (slot.isBooked()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu saat artıq tutulub");
        }
        if (slot.getStartTime().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keçmiş vaxta rezerv olmaz");
        }
        slot.book(req.name(), req.phone());
        return new SlotDto(slot.getId(), slot.getStartTime());
    }

    private void requireShop(Long id) {
        if (!shops.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }
}
