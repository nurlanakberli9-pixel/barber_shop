package com.barber.booking;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkRangeRepository extends JpaRepository<WorkRange, Long> {
    List<WorkRange> findByBarbershopIdOrderByStartMinute(Long barbershopId);
}
