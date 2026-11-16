package com.barber.booking;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkWindowRepository extends JpaRepository<WorkWindow, Long> {
    List<WorkWindow> findByBarbershopIdAndWindowDateOrderByStartTime(
            Long barbershopId, LocalDate windowDate);

    Optional<WorkWindow> findByIdAndBarbershopId(Long id, Long barbershopId);

    @EntityGraph(attributePaths = "cubes")
    List<WorkWindow> findByBarbershopIdAndWindowDateBetweenOrderByWindowDateAscStartTimeAsc(
            Long barbershopId, LocalDate from, LocalDate to);
}
