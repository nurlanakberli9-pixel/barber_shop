package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class ServiceTests {

    private static final String URL = "/api/panel/services";

    @Autowired
    WebApplicationContext ctx;
    @Autowired
    AppUserRepository users;
    @Autowired
    BarbershopRepository shops;
    @Autowired
    PasswordEncoder encoder;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
        Barbershop a = shops.save(new Barbershop("Svc A", "Alpha", "A küç. 1", 4.0));
        Barbershop b = shops.save(new Barbershop("Svc B", "Beta", "B küç. 2", 4.0));
        users.save(new AppUser("svca", encoder.encode("x-Pass-123"), "BARBER", a));
        users.save(new AppUser("svcb", encoder.encode("x-Pass-123"), "BARBER", b));
    }

    private String body(int h, int w, int b) {
        return String.format("{\"services\":[{\"code\":\"HAIRCUT\",\"durationMinutes\":%d},"
                + "{\"code\":\"WASH\",\"durationMinutes\":%d},"
                + "{\"code\":\"BEARD\",\"durationMinutes\":%d}]}", h, w, b);
    }

    private MockHttpServletResponse fetch(String who) throws Exception {
        return mvc.perform(get(URL).with(user(who).roles("BARBER"))).andReturn().getResponse();
    }

    private MockHttpServletResponse save(String who, String json) throws Exception {
        return mvc.perform(put(URL).with(user(who).roles("BARBER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn().getResponse();
    }

    private int count(String text, String part) {
        return text.split(Pattern.quote(part), -1).length - 1;
    }

    @Test
    void servicesWithoutLoginIs401() throws Exception {
        assertEquals(401, mvc.perform(get(URL)).andReturn().getResponse().getStatus());
    }

    @Test
    void firstGetCreatesThreeEmptyServices() throws Exception {
        var res = fetch("svca");
        assertEquals(200, res.getStatus());
        String body = res.getContentAsString();
        assertEquals(1, count(body, "\"HAIRCUT\""));
        assertEquals(1, count(body, "\"WASH\""));
        assertEquals(1, count(body, "\"BEARD\""));
        assertEquals(3, count(body, "\"durationMinutes\":null"));
        assertTrue(body.contains("\"minimumWindowMinutes\":null"));
    }

    @Test
    void secondGetDoesNotDuplicateServices() throws Exception {
        fetch("svca");
        String body = fetch("svca").getContentAsString();
        assertEquals(1, count(body, "\"HAIRCUT\""));
        assertEquals(3, count(body, "\"durationMinutes\""));
    }

    @Test
    void savedDurationsAreReturnedWithSumAndPersist() throws Exception {
        var res = save("svca", body(30, 15, 20));
        assertEquals(200, res.getStatus());
        assertTrue(res.getContentAsString().contains("\"minimumWindowMinutes\":65"));
        String again = fetch("svca").getContentAsString();
        assertTrue(again.contains("\"durationMinutes\":30"));
        assertTrue(again.contains("\"durationMinutes\":15"));
        assertTrue(again.contains("\"durationMinutes\":20"));
    }

    @Test
    void saveWithoutCsrfTokenIs403() throws Exception {
        int status = mvc.perform(put(URL).with(user("svca").roles("BARBER"))
                .contentType(MediaType.APPLICATION_JSON).content(body(30, 15, 20)))
                .andReturn().getResponse().getStatus();
        assertEquals(403, status);
    }

    @Test
    void invalidRequestsAre400() throws Exception {
        String[] bad = {
            body(3, 15, 20),
            body(30, 15, 241),
            body(30, 12, 20),
            "{\"services\":[{\"code\":\"HAIRCUT\",\"durationMinutes\":30},"
                    + "{\"code\":\"WASH\",\"durationMinutes\":15}]}",
            "{\"services\":[{\"code\":\"HAIRCUT\",\"durationMinutes\":30},"
                    + "{\"code\":\"HAIRCUT\",\"durationMinutes\":30},"
                    + "{\"code\":\"WASH\",\"durationMinutes\":15}]}",
            "{\"services\":[{\"code\":\"HAIRCUT\",\"durationMinutes\":30},"
                    + "{\"code\":\"WASH\",\"durationMinutes\":15},"
                    + "{\"code\":\"COLOR\",\"durationMinutes\":20}]}",
            "{\"services\":[{\"code\":\"HAIRCUT\",\"durationMinutes\":30},"
                    + "{\"code\":\"WASH\",\"durationMinutes\":15},"
                    + "{\"code\":\"BEARD\"}]}",
            "{bu json deyil"
        };
        for (String json : bad) {
            assertEquals(400, save("svca", json).getStatus(), json);
        }
    }

    @Test
    void barbersDoNotShareDurations() throws Exception {
        assertEquals(200, save("svca", body(30, 15, 20)).getStatus());
        String other = fetch("svcb").getContentAsString();
        assertTrue(other.contains("\"minimumWindowMinutes\":null"));
        assertEquals(3, count(other, "\"durationMinutes\":null"));
    }
}
