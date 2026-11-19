package com.barber.booking;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClosedDayRepository extends JpaRepository<ClosedDay, Long> {
    List<ClosedDay> findByBarbershopIdAndClosedDateGreaterThanEqualOrderByClosedDate(
            Long barbershopId, LocalDate from);

    List<ClosedDay> findByBarbershopIdAndClosedDateBetween(
            Long barbershopId, LocalDate from, LocalDate to);

    boolean existsByBarbershopIdAndClosedDate(Long barbershopId, LocalDate date);

    long deleteByBarbershopIdAndClosedDate(Long barbershopId, LocalDate date);
}
