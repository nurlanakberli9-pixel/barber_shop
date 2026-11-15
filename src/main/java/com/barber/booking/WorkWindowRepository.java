package com.barber.booking;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkWindowRepository extends JpaRepository<WorkWindow, Long> {
    List<WorkWindow> findByBarbershopIdAndWindowDateOrderByStartTime(
            Long barbershopId, LocalDate windowDate);
}
