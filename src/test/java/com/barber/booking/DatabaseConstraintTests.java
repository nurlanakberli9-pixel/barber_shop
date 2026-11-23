package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class DatabaseConstraintTests {

    @Autowired
    DataSource dataSource;
    @Autowired
    BarbershopRepository shops;

    JdbcTemplate jdbc;
    long shopId;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        shopId = shops.saveAndFlush(new Barbershop("Constraint Salon", "Master", "Addr 1", 4.0)).getId();
    }

    private void insertBooking(long shop, int start, int duration, int buffer) {
        jdbc.update("insert into booking (barbershop_id, booking_date, start_minute, duration_minutes, "
                        + "buffer_minutes, services, customer_name, customer_phone, created_at) "
                        + "values (?, ?, ?, ?, ?, 'WASH', 'Elvin', '+994501234567', ?)",
                shop, Date.valueOf(LocalDate.now().plusDays(1)), start, duration, buffer,
                Timestamp.valueOf(LocalDateTime.now()));
    }

    @Test
    void nullNameIsRejected() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into barbershop (name, master_name, address, rating) values (null, 'M', 'A', 4.0)"));
    }

    @Test
    void ratingOutsideZeroToFiveIsRejected() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into barbershop (name, master_name, address, rating) values ('S', 'M', 'A', 7.0)"));
    }

    @Test
    void bookingForAnUnknownShopIsRejected() {
        assertThrows(DataIntegrityViolationException.class, () -> insertBooking(999999L, 480, 20, 0));
    }

    @Test
    void bookingWithZeroDurationIsRejected() {
        assertThrows(DataIntegrityViolationException.class, () -> insertBooking(shopId, 480, 0, 0));
    }

    @Test
    void validBookingIsAccepted() {
        insertBooking(shopId, 480, 20, 0);
        Integer n = jdbc.queryForObject("select count(*) from booking where barbershop_id = ?",
                Integer.class, shopId);
        assertEquals(1, n);
    }

    @Test
    void duplicateClosedDayIsRejected() {
        Date d = Date.valueOf(LocalDate.now().plusDays(3));
        jdbc.update("insert into closed_day (barbershop_id, closed_date) values (?, ?)", shopId, d);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into closed_day (barbershop_id, closed_date) values (?, ?)", shopId, d));
    }

    @Test
    void duplicateUsernameAndUnknownRoleAreRejected() {
        jdbc.update("insert into app_user (username, password_hash, role) values ('dbuser1', 'h', 'BARBER')");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into app_user (username, password_hash, role) values ('dbuser1', 'h', 'BARBER')"));
    }

    @Test
    void unknownRoleAndBrokenServiceOrderAreRejectedByCheckConstraints() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into app_user (username, password_hash, role) values ('dbuser2', 'h', 'HACKER')"));
    }
}
