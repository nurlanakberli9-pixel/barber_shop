package com.barber.booking;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);

    List<AppUser> findByRoleOrderById(String role);

    @Query("select u.barbershop.id from AppUser u where u.username = :username")
    Optional<Long> findShopIdByUsername(@Param("username") String username);
}
