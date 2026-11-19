package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
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
class PagesTests {

    @Autowired
    WebApplicationContext ctx;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
    }

    private String page(String url) throws Exception {
        var res = mvc.perform(get(url)).andReturn().getResponse();
        assertEquals(200, res.getStatus(), url);
        return res.getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void customerPageHasExcelLikeGridAndNoAverage() throws Exception {
        String html = page("/index.html");
        assertTrue(html.contains("/day?date="));
        assertTrue(html.contains("/bookings"));
        assertTrue(html.contains("/calendar?days=60"));
        assertTrue(html.contains("/services"));
        assertTrue(html.contains("minhead"));
        assertTrue(html.contains("hlRow"));
        assertTrue(html.contains("hlCol"));
        assertFalse(html.contains("avgMinutes"));
        assertFalse(html.contains("minMinutes"));
        assertFalse(html.contains("/windows"));
        assertFalse(html.contains("/api/slots/"));
        assertFalse(html.contains("innerHTML"));
    }

    @Test
    void panelPageHasFiveTilesAndStepperServices() throws Exception {
        String html = page("/panel.html");
        assertTrue(html.contains("function showPanel"));
        assertTrue(html.contains("/api/panel/hours"));
        assertTrue(html.contains("/api/panel/services"));
        assertTrue(html.contains("/api/panel/buffers"));
        assertTrue(html.contains("/api/panel/bookings"));
        assertTrue(html.contains("sval"));
        assertFalse(html.contains("minMinutes"));
        assertFalse(html.contains("/api/panel/windows"));
        assertFalse(html.contains("/api/panel/day"));
        assertFalse(html.contains("/api/panel/slots"));
        assertFalse(html.contains("innerHTML"));
    }

    @Test
    void shopsListHasMasterAndRatingButNoAccountData() throws Exception {
        String body = mvc.perform(get("/api/shops")).andReturn().getResponse().getContentAsString();
        assertTrue(body.split(Pattern.quote("\"masterName\""), -1).length - 1 >= 8, body);
        assertTrue(body.contains("\"rating\""));
        String lower = body.toLowerCase();
        assertFalse(lower.contains("password"));
        assertFalse(lower.contains("username"));
        assertFalse(lower.contains("phone"));
    }

    @Test
    void oldEndpointsAreClosed() throws Exception {
        assertEquals(404, mvc.perform(get("/api/shops/1/windows?date=2030-01-01"))
                .andReturn().getResponse().getStatus());
        assertEquals(401, mvc.perform(post("/api/slots/1/book").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getStatus());
        assertEquals(404, mvc.perform(get("/api/panel/windows?date=2030-01-01")
                .with(user("x").roles("BARBER"))).andReturn().getResponse().getStatus());
    }
}
