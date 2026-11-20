package com.barber.booking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByBarbershopIdAndBookingDateOrderByStartMinute(
            Long barbershopId, LocalDate bookingDate);

    List<Booking> findByBarbershopIdAndBookingDateBetween(
            Long barbershopId, LocalDate from, LocalDate to);

    List<Booking> findByBarbershopIdAndBookingDateGreaterThanEqual(
            Long barbershopId, LocalDate from);

    Optional<Booking> findByIdAndBarbershopId(Long id, Long barbershopId);

    long countByCustomerPhoneInAndCreatedAtAfter(Collection<String> phones, LocalDateTime since);

    long countByBarbershopIdAndCustomerPhoneInAndBookingDateGreaterThanEqual(
            Long barbershopId, Collection<String> phones, LocalDate from);
}
