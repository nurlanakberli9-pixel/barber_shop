package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class PublicPageTests {

    @Autowired
    WebApplicationContext ctx;

    @Test
    void customerPageUsesWindowsAndCubesNotOldHourSlots() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
        String html = mvc.perform(get("/index.html")).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertTrue(html.contains("/windows?date="));
        assertTrue(html.contains("/bookings"));
        assertTrue(html.contains("/calendar?days=60"));
        assertFalse(html.contains("/api/slots/"));
        assertFalse(html.contains("/day?date="));
        assertFalse(html.contains("innerHTML"));
    }
}
