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

    private String asset(String name) throws Exception {
        return page("/" + name + ".html") + page("/css/" + name + ".css") + page("/js/" + name + ".js");
    }

    @Test
    void panelHasClosedDaysAndAccountSections() throws Exception {
        String all = asset("panel");
        assertTrue(all.contains("function closedPanel"));
        assertTrue(all.contains("function accountPanel"));
        assertTrue(all.contains("/api/panel/closed"));
        assertTrue(all.contains("/api/panel/push"));
        assertTrue(all.contains("/api/panel/password"));
        assertTrue(all.contains("İstirahət günləri"));
        assertTrue(all.contains("cancelBookingIds"));
        assertFalse(all.contains("innerHTML"));
    }

    @Test
    void adminPageAndServiceWorkerArePublicButAdminApiIsNot() throws Exception {
        String admin = asset("admin");
        assertTrue(admin.contains("/api/admin/barbers"));
        assertFalse(admin.contains("innerHTML"));
        String sw = page("/sw.js");
        assertTrue(sw.contains("showNotification"));
        assertTrue(sw.contains("notificationclick"));
        assertEquals(401, mvc.perform(get("/api/admin/barbers")).andReturn().getResponse().getStatus());
        assertEquals(401, mvc.perform(get("/api/admin/me")).andReturn().getResponse().getStatus());
    }

    @Test
    void customerPageLabelsUnratedShopsAsNewAndHandlesRateLimit() throws Exception {
        String all = asset("index");
        assertTrue(all.contains("return 'Yeni'"));
        assertTrue(all.contains("st === 429"));
        assertFalse(all.contains("innerHTML"));
    }
}
