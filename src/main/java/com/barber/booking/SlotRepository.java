package com.barber.booking;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlotRepository extends JpaRepository<Slot, Long> {
    List<Slot> findByBarbershopIdAndBookedFalseAndStartTimeBetweenOrderByStartTime(
            Long barbershopId, LocalDateTime from, LocalDateTime to);

    List<Slot> findByBarbershopIdAndStartTimeBetweenOrderByStartTime(
            Long barbershopId, LocalDateTime from, LocalDateTime to);
}
