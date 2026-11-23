package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "app.rate-limit.enabled=true")
class RequestIdIntegrationTests {

    private static final String UUID_RE = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

    @Autowired
    WebApplicationContext ctx;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
    }

    private MockHttpServletResponse fetch(String url, String ip) throws Exception {
        return mvc.perform(get(url).with(r -> { r.setRemoteAddr(ip); return r; }))
                .andReturn().getResponse();
    }

    @Test
    void everyApiResponseCarriesARequestIdAndPagesDoNot() throws Exception {
        for (String url : new String[] {"/api/shops", "/api/hack"}) {
            String id = fetch(url, "198.51.100.10").getHeader("X-Request-Id");
            assertNotNull(id, url);
            assertTrue(id.matches(UUID_RE), id);
        }
        assertNull(fetch("/index.html", "198.51.100.10").getHeader("X-Request-Id"));
    }

    @Test
    void rateLimitedResponsesAlsoCarryTheRequestId() throws Exception {
        MockHttpServletResponse last = null;
        for (int i = 0; i < 6; i++) {
            last = mvc.perform(post("/api/shops/1/bookings").contentType(MediaType.APPLICATION_JSON)
                    .content("{}").with(r -> { r.setRemoteAddr("198.51.100.77"); return r; }))
                    .andReturn().getResponse();
            if (i < 5) assertEquals(400, last.getStatus(), "i=" + i);
        }
        assertEquals(429, last.getStatus());
        assertNotNull(last.getHeader("X-Request-Id"));
        assertNotNull(last.getHeader("Retry-After"));
    }
}
