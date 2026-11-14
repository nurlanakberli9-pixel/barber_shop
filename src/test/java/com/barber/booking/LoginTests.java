package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class LoginTests {

    private static final String PASS = "Test-Pass-123";
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
        Barbershop shop = shops.save(new Barbershop("Test Salon", "Tester", "Test küç. 1", 4.0));
        users.save(new AppUser("tester", encoder.encode(PASS), "BARBER", shop));
        addr = "10.1.0." + NEXT_IP.getAndIncrement();
    }

    private MockHttpServletRequestBuilder login(String user, String pass) {
        return post("/api/login").param("username", user).param("password", pass)
                .with(csrf())
                .with(request -> { request.setRemoteAddr(addr); return request; });
    }

    @Test
    void panelWithoutLoginIs401() throws Exception {
        mvc.perform(get("/api/panel/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithoutCsrfTokenIs403() throws Exception {
        mvc.perform(post("/api/login").param("username", "tester").param("password", PASS))
                .andExpect(status().isForbidden());
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        mvc.perform(login("tester", "yanlis")).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownUserGivesSameAnswerAsWrongPassword() throws Exception {
        mvc.perform(login("yoxdur", "yanlis")).andExpect(status().isUnauthorized());
    }

    @Test
    void correctLoginThenPanelWorks() throws Exception {
        MvcResult r = mvc.perform(login("tester", PASS)).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) r.getRequest().getSession(false);
        assertNotNull(session);
        String body = mvc.perform(get("/api/panel/me").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("Tester"));
        assertFalse(body.toLowerCase().contains("password"));
    }

    @Test
    void fiveFailuresLockOutEvenTheCorrectPassword() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(login("tester", "yanlis" + i)).andExpect(status().isUnauthorized());
        }
        mvc.perform(login("tester", PASS)).andExpect(status().isTooManyRequests());
    }

    @Test
    void publicEndpointsStayOpenAndUnknownPathsAreDenied() throws Exception {
        mvc.perform(get("/api/shops")).andExpect(status().isOk());
        mvc.perform(get("/api/hack")).andExpect(status().isUnauthorized());
    }

    @Test
    void bookingNeedsNoCsrfToken() throws Exception {
        mvc.perform(post("/api/slots/1/book").contentType("application/json")
                .content("{\"name\":\"\",\"phone\":\"1\"}"))
                .andExpect(status().isBadRequest());
    }
}
