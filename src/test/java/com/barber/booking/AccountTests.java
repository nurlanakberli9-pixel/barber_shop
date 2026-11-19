package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class AccountTests {

    private static final String PASS = "Current-Pass-123";
    private static final String URL = "/api/panel/password";
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired
    WebApplicationContext ctx;
    @Autowired
    AppUserRepository users;
    @Autowired
    BarbershopRepository shops;
    @Autowired
    PasswordEncoder encoder;

    MockMvc mvc;
    String addr;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
        Barbershop shop = shops.save(new Barbershop("Acc Salon", "Accman", "Acc kuc. 1", 4.0));
        users.save(new AppUser("accuser", encoder.encode(PASS), "BARBER", shop));
        addr = "10.3.0." + NEXT_IP.getAndIncrement();
    }

    private String json(String current, String next) {
        return "{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}";
    }

    private int change(String body) throws Exception {
        return mvc.perform(post(URL).with(user("accuser").roles("BARBER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus();
    }

    private int login(String password) throws Exception {
        return mvc.perform(post("/api/login").param("username", "accuser")
                .param("password", password).with(csrf())
                .with(request -> { request.setRemoteAddr(addr); return request; }))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void changeNeedsLoginAndCsrfToken() throws Exception {
        assertEquals(401, mvc.perform(post(URL).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json(PASS, "New-Pass-45678"))).andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(post(URL).with(user("accuser").roles("BARBER"))
                .contentType(MediaType.APPLICATION_JSON).content(json(PASS, "New-Pass-45678")))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void wrongCurrentPasswordIs422() throws Exception {
        assertEquals(422, change(json("Wrong-Pass-000", "New-Pass-45678")));
        assertEquals(200, login(PASS));
    }

    @Test
    void invalidNewPasswordsAre400() throws Exception {
        String[] bad = {
            json(PASS, "short"),
            json(PASS, "p".repeat(73)),
            json(PASS, "\u0259".repeat(40)),
            json(PASS, PASS),
            "{\"currentPassword\":\"" + PASS + "\"}",
            "{bu json deyil"
        };
        for (String b : bad) {
            assertEquals(400, change(b), b);
        }
        assertEquals(200, login(PASS));
    }

    @Test
    void changeSwitchesTheLoginPassword() throws Exception {
        assertEquals(204, change(json(PASS, "New-Pass-45678")));
        assertEquals(401, login(PASS));
        assertEquals(200, login("New-Pass-45678"));
    }
}
