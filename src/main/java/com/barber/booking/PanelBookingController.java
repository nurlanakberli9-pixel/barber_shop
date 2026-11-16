package com.barber.booking;

import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/panel/bookings")
public class PanelBookingController {

    record ServiceRef(String code, String name) {}
    record BookingRow(Long id, String start, String end, int minutes,
            List<ServiceRef> services, String customerName, String customerPhone) {}

    private final AppUserRepository users;
    private final BarbershopRepository shops;
    private final BookingRepository bookings;

    public PanelBookingController(AppUserRepository users, BarbershopRepository shops,
            BookingRepository bookings) {
        this.users = users;
        this.shops = shops;
        this.bookings = bookings;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<BookingRow> day(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication auth) {
        Barbershop shop = myShop(auth);
        LocalDate today = LocalDate.now();
        if (date.isBefore(today) || date.isAfter(today.plusDays(59))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tarix bugündən 60 günlük aralıqda olmalıdır");
        }
        return bookings.findByBarbershopIdAndBookingDateOrderByStartMinute(shop.getId(), date)
                .stream()
                .map(b -> new BookingRow(b.getId(), DayPlanner.fmt(b.getStartMinute()),
                        DayPlanner.fmt(b.getEndMinute()), b.getDurationMinutes(),
                        b.getKinds().stream()
                                .map(k -> new ServiceRef(k.name(), k.getLabel())).toList(),
                        b.getCustomerName(), b.getCustomerPhone()))
                .toList();
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void cancel(@PathVariable Long id, Authentication auth) {
        Barbershop shop = myShop(auth);
        shops.findByIdForUpdate(shop.getId());
        Booking b = bookings.findByIdAndBarbershopId(id, shop.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        bookings.delete(b);
    }

    private Barbershop myShop(Authentication auth) {
        AppUser u = users.findByUsername(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Barbershop b = u.getBarbershop();
        if (b == null) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        return b;
    }
}
