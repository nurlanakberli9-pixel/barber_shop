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

    private String asset(String name) throws Exception {
        return page("/" + name + ".html") + page("/css/" + name + ".css") + page("/js/" + name + ".js");
    }

    @Test
    void customerPageHasExcelLikeGridAndNoAverage() throws Exception {
        String all = asset("index");
        assertTrue(all.contains("/day?date="));
        assertTrue(all.contains("/bookings"));
        assertTrue(all.contains("/calendar?days=60"));
        assertTrue(all.contains("/services"));
        assertTrue(all.contains("minhead"));
        assertTrue(all.contains("hlRow"));
        assertTrue(all.contains("hlCol"));
        assertFalse(all.contains("avgMinutes"));
        assertFalse(all.contains("minMinutes"));
        assertFalse(all.contains("/windows"));
        assertFalse(all.contains("/api/slots/"));
        assertFalse(all.contains("innerHTML"));
    }

    @Test
    void panelPageHasFiveTilesAndStepperServices() throws Exception {
        String all = asset("panel");
        assertTrue(all.contains("function showPanel"));
        assertTrue(all.contains("/api/panel/hours"));
        assertTrue(all.contains("/api/panel/services"));
        assertTrue(all.contains("/api/panel/buffers"));
        assertTrue(all.contains("/api/panel/bookings"));
        assertTrue(all.contains("sval"));
        assertFalse(all.contains("minMinutes"));
        assertFalse(all.contains("/api/panel/windows"));
        assertFalse(all.contains("/api/panel/day"));
        assertFalse(all.contains("/api/panel/slots"));
        assertFalse(all.contains("innerHTML"));
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
