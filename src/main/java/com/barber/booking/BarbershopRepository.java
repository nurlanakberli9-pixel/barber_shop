package com.barber.booking;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BarbershopRepository extends JpaRepository<Barbershop, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Barbershop b where b.id = :id")
    Optional<Barbershop> findByIdForUpdate(@Param("id") Long id);
}
