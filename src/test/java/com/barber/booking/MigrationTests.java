package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class MigrationTests {

    @Autowired
    DataSource dataSource;
    @Value("${spring.jpa.hibernate.ddl-auto}")
    String ddlAuto;

    @Test
    void schemaIsManagedByFlywayAndHibernateOnlyValidates() {
        assertEquals("validate", ddlAuto);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer n = jdbc.queryForObject(
                "select count(*) from \"flyway_schema_history\" where \"success\" = true and \"version\" = '1'",
                Integer.class);
        assertEquals(1, n);
    }

    @Test
    void allTablesExist() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        for (String t : List.of("barbershop", "app_user", "service_type", "work_range",
                "combo_buffer", "booking", "closed_day", "push_subscription")) {
            Integer n = jdbc.queryForObject(
                    "select count(*) from information_schema.tables where lower(table_name) = ?",
                    Integer.class, t);
            assertEquals(1, n, t);
        }
    }

    @Test
    void seededTestDataIsPresentThroughTheMigratedSchema() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer shops = jdbc.queryForObject("select count(*) from barbershop", Integer.class);
        assertTrue(shops >= 8, "shops=" + shops);
    }
}
