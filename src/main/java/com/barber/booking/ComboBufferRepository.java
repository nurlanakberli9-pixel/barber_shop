package com.barber.booking;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComboBufferRepository extends JpaRepository<ComboBuffer, Long> {
    List<ComboBuffer> findByBarbershopId(Long barbershopId);
}
