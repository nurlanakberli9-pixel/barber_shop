package com.barber.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class BookingController {

    record ShopDto(Long id, String name, String address) {}
    record SlotDto(Long id, LocalDateTime startTime) {}
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
                .map(s -> new ShopDto(s.getId(), s.getName(), s.getAddress()))
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
}
