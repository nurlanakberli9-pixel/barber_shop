package com.barber.booking;

import com.barber.booking.DayPlanner.Span;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
@RequestMapping("/api/panel/hours")
public class HoursController {

    private static final int MIN_START = 5 * 60;
    private static final int MAX_END = 23 * 60 + 55;

    record RangeDto(String start, String end) {}
    record RangeIn(
            @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String start,
            @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$") String end) {}
    record HoursRequest(@NotNull @Size(min = 1, max = 6) List<@NotNull @Valid RangeIn> ranges) {}

    private final AppUserRepository users;
    private final BarbershopRepository shops;
    private final WorkRangeRepository workRanges;
    private final BookingRepository bookings;

    public HoursController(AppUserRepository users, BarbershopRepository shops,
            WorkRangeRepository workRanges, BookingRepository bookings) {
        this.users = users;
        this.shops = shops;
        this.workRanges = workRanges;
        this.bookings = bookings;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<RangeDto> get(Authentication auth) {
        Long shopId = shopId(auth);
        return workRanges.findByBarbershopIdOrderByStartMinute(shopId).stream()
                .map(r -> new RangeDto(DayPlanner.fmt(r.getStartMinute()),
                        DayPlanner.fmt(r.getEndMinute())))
                .toList();
    }

    @PutMapping
    @Transactional
    public List<RangeDto> put(@Valid @RequestBody HoursRequest req, Authentication auth) {
        Barbershop shop = shops.findByIdForUpdate(shopId(auth))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<Span> spans = new ArrayList<>();
        for (RangeIn r : req.ranges()) {
            int s = DayPlanner.parse(r.start());
            int e = DayPlanner.parse(r.end());
            if (s % DayPlanner.STEP != 0 || e % DayPlanner.STEP != 0 || e <= s
                    || s < MIN_START || e > MAX_END) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Saat aralığı düzgün deyil (05:00-23:55, 5 dəqiqənin qatı)");
            }
            spans.add(new Span(s, e));
        }
        List<Span> merged = DayPlanner.merge(spans);
        LocalDateTime now = LocalDateTime.now();
        int nowMinute = now.getHour() * 60 + now.getMinute();
        for (Booking b : bookings.findByBarbershopIdAndBookingDateGreaterThanEqual(
                shop.getId(), now.toLocalDate())) {
            boolean upcoming = b.getBookingDate().isAfter(now.toLocalDate())
                    || b.getEndMinute() > nowMinute;
            if (upcoming && !DayPlanner.covered(merged, b.getStartMinute(), b.getEndMinute())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Mövcud rezervlər yeni iş saatlarından kənarda qalır, əvvəl onları ləğv edin");
            }
        }
        workRanges.deleteAll(workRanges.findByBarbershopIdOrderByStartMinute(shop.getId()));
        workRanges.flush();
        for (Span sp : merged) workRanges.save(new WorkRange(shop, sp.start(), sp.end()));
        return merged.stream()
                .map(sp -> new RangeDto(DayPlanner.fmt(sp.start()), DayPlanner.fmt(sp.end())))
                .toList();
    }

    private Long shopId(Authentication auth) {
        AppUser u = users.findByUsername(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Barbershop b = u.getBarbershop();
        if (b == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return b.getId();
    }
}
