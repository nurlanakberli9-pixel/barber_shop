package com.barber.booking;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceTypeRepository extends JpaRepository<ServiceType, Long> {
    List<ServiceType> findByBarbershopIdOrderById(Long barbershopId);
}
