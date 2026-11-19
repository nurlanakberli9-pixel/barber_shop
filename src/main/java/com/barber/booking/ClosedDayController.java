package com.barber.booking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/panel/closed")
public class ClosedDayController {

    private final AppUserRepository users;
    private final BarbershopRepository shops;
    private final ClosedDayRepository closed;
    private final BookingRepository bookings;

    public ClosedDayController(AppUserRepository users, BarbershopRepository shops,
            ClosedDayRepository closed, BookingRepository bookings) {
        this.users = users;
        this.shops = shops;
        this.closed = closed;
        this.bookings = bookings;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<String> list(Authentication auth) {
        Long shopId = shopId(auth);
        return closed.findByBarbershopIdAndClosedDateGreaterThanEqualOrderByClosedDate(
                        shopId, LocalDate.now())
                .stream().map(c -> c.getClosedDate().toString()).toList();
    }

    @PostMapping("/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void close(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) List<Long> cancelBookingIds,
            Authentication auth) {
        Barbershop shop = shops.findByIdForUpdate(shopId(auth))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        checkDate(date);
        LocalDateTime now = LocalDateTime.now();
        int nowMinute = now.getHour() * 60 + now.getMinute();
        Set<Long> cancel = new HashSet<>(cancelBookingIds == null ? List.<Long>of() : cancelBookingIds);
        List<Booking> blocking = bookings
                .findByBarbershopIdAndBookingDateOrderByStartMinute(shop.getId(), date).stream()
                .filter(b -> date.isAfter(now.toLocalDate()) || b.getEndMinute() > nowMinute)
                .toList();
        for (Booking b : blocking) {
            if (!cancel.contains(b.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Bu gündə rezervlər var, əvvəl onları ləğv edin");
            }
        }
        bookings.deleteAll(blocking);
        if (!closed.existsByBarbershopIdAndClosedDate(shop.getId(), date)) {
            closed.save(new ClosedDay(shop, date));
        }
    }

    @DeleteMapping("/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void reopen(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication auth) {
        Long shopId = shopId(auth);
        checkDate(date);
        closed.deleteByBarbershopIdAndClosedDate(shopId, date);
    }

    private static void checkDate(LocalDate date) {
        LocalDate today = LocalDate.now();
        if (date.isBefore(today) || date.isAfter(today.plusDays(59))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tarix bugündən 60 günlük aralıqda olmalıdır");
        }
    }

    private Long shopId(Authentication auth) {
        AppUser u = users.findByUsername(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Barbershop b = u.getBarbershop();
        if (b == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return b.getId();
    }
}
