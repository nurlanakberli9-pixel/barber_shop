package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class ShopApiTests {

    @Autowired
    WebApplicationContext ctx;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
    }

    private int status(org.springframework.test.web.servlet.RequestBuilder rb) throws Exception {
        return mvc.perform(rb).andReturn().getResponse().getStatus();
    }

    @Test
    void shopsListHasMasterAndRating() throws Exception {
        String body = mvc.perform(get("/api/shops")).andReturn().getResponse().getContentAsString();
        assertTrue(body.split(Pattern.quote("\"masterName\""), -1).length - 1 >= 8, body);
        assertTrue(body.contains("\"rating\""));
        assertTrue(body.contains("\"address\""));
    }

    @Test
    void shopsListLeaksNoAccountData() throws Exception {
        String body = mvc.perform(get("/api/shops")).andReturn().getResponse()
                .getContentAsString().toLowerCase();
        assertFalse(body.contains("password"));
        assertFalse(body.contains("username"));
        assertFalse(body.contains("phone"));
    }

    @Test
    void oldPublicGetEndpointsAreGone() throws Exception {
        String date = "?date=" + LocalDate.now().plusDays(1);
        assertEquals(404, status(get("/api/shops/1/slots" + date)));
        assertEquals(404, status(get("/api/shops/1/day" + date)));
        assertEquals(404, status(get("/api/shops/1/availability")));
    }

    @Test
    void oldBookingEndpointIsClosed() throws Exception {
        assertEquals(401, status(post("/api/slots/1/book").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Elvin\",\"phone\":\"+994501234567\"}")));
    }

    @Test
    void oldPanelSlotEndpointIsGone() throws Exception {
        assertEquals(404, status(get("/api/panel/day?date=" + LocalDate.now().plusDays(1))
                .with(user("x").roles("BARBER"))));
    }

    @Test
    void panelPageNoLongerCallsOldEndpoints() throws Exception {
        String html = mvc.perform(get("/panel.html")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertFalse(html.contains("/api/panel/day"));
        assertFalse(html.contains("/api/panel/slots"));
        assertFalse(html.contains("Köhnə saatlar"));
        assertTrue(html.contains("function showPanel"));
    }
}
