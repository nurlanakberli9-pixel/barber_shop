package com.barber.booking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByBarbershopIdAndBookingDateOrderByStartMinute(
            Long barbershopId, LocalDate bookingDate);

    List<Booking> findByBarbershopIdAndBookingDateBetween(
            Long barbershopId, LocalDate from, LocalDate to);

    Optional<Booking> findByIdAndBarbershopId(Long id, Long barbershopId);
}
