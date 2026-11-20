package com.barber.booking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class BookingAbuseGuard {

    private final BookingRepository bookings;
    private final boolean enabled;
    private final int perDay;
    private final int upcomingPerShop;

    public BookingAbuseGuard(BookingRepository bookings,
            @Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.abuse.max-per-day:6}") int perDay,
            @Value("${app.abuse.max-upcoming-per-shop:3}") int upcomingPerShop) {
        this.bookings = bookings;
        this.enabled = enabled;
        this.perDay = perDay;
        this.upcomingPerShop = upcomingPerShop;
    }

    public void check(Long shopId, String phone) {
        if (!enabled) return;
        List<String> variants = variants(phone);
        long today = bookings.countByCustomerPhoneInAndCreatedAtAfter(
                variants, LocalDateTime.now().minusHours(24));
        if (today >= perDay) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Gündəlik limit dolub");
        }
        long upcoming = bookings.countByBarbershopIdAndCustomerPhoneInAndBookingDateGreaterThanEqual(
                shopId, variants, LocalDate.now());
        if (upcoming >= upcomingPerShop) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Bu salonda rezerv limiti dolub");
        }
    }

    static List<String> variants(String phone) {
        String digits = phone.startsWith("+") ? phone.substring(1) : phone;
        return List.of(digits, "+" + digits);
    }
}
