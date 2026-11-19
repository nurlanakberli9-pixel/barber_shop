package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class PagesExtraTests {

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
    void panelHasClosedDaysAndAccountSections() throws Exception {
        String html = page("/panel.html");
        assertTrue(html.contains("function closedPanel"));
        assertTrue(html.contains("function accountPanel"));
        assertTrue(html.contains("/api/panel/closed"));
        assertTrue(html.contains("/api/panel/push"));
        assertTrue(html.contains("/api/panel/password"));
        assertTrue(html.contains("İstirahət günləri"));
        assertTrue(html.contains("cancelBookingIds"));
        assertFalse(html.contains("innerHTML"));
    }

    @Test
    void adminPageAndServiceWorkerArePublicButAdminApiIsNot() throws Exception {
        String admin = page("/admin.html");
        assertTrue(admin.contains("/api/admin/barbers"));
        assertFalse(admin.contains("innerHTML"));
        String sw = page("/sw.js");
        assertTrue(sw.contains("showNotification"));
        assertTrue(sw.contains("notificationclick"));
        assertEquals(401, mvc.perform(get("/api/admin/barbers")).andReturn().getResponse().getStatus());
        assertEquals(401, mvc.perform(get("/api/admin/me")).andReturn().getResponse().getStatus());
    }

    @Test
    void customerPageLabelsUnratedShopsAsNew() throws Exception {
        String html = page("/index.html");
        assertTrue(html.contains("return 'Yeni'"));
        assertFalse(html.contains("innerHTML"));
    }
}
