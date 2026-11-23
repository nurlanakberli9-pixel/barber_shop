package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ServiceOrderConstraintTests {

    @Autowired
    DataSource dataSource;
    @Autowired
    BarbershopRepository shops;

    @Test
    void lowAboveMediumIsRejectedByTheDatabase() {
        long id = shops.saveAndFlush(new Barbershop("Order Salon", "Master", "Addr 1", 4.0)).getId();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into service_type (barbershop_id, kind, low_minutes, medium_minutes, high_minutes) "
                        + "values (?, 'HAIRCUT', 30, 20, 25)", id));
    }
}
