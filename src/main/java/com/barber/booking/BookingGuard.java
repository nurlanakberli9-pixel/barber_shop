package com.barber.booking;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Component
public class BookingGuard {

    private final BarbershopRepository shops;
    private final BookingRepository bookings;

    public BookingGuard(BarbershopRepository shops, BookingRepository bookings) {
        this.shops = shops;
        this.bookings = bookings;
    }

    @Transactional
    public void assertBookingsStillFit(Long shopId, LocalDate date, List<int[]> newSpans,
            Set<Long> cancelIds) {
        shops.findByIdForUpdate(shopId);
        List<DayPlanner.Span> merged = DayPlanner.merge(
                newSpans.stream().map(a -> new DayPlanner.Span(a[0], a[1])).toList());
        List<Booking> stranded = new ArrayList<>();
        for (Booking b : bookings.findByBarbershopIdAndBookingDateOrderByStartMinute(shopId, date)) {
            if (!DayPlanner.covered(merged, b.getStartMinute(), b.getEndMinute())) {
                stranded.add(b);
            }
        }
        for (Booking b : stranded) {
            if (!cancelIds.contains(b.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Rezervi olan vaxtı silmək olmaz, əvvəl rezervi ləğv edin");
            }
        }
        bookings.deleteAll(stranded);
    }
}
