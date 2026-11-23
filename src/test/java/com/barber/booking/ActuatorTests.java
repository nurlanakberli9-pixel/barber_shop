package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class ActuatorTests {

    @Autowired
    WebApplicationContext ctx;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
    }

    private MockHttpServletResponse fetch(String url) throws Exception {
        return mvc.perform(get(url)).andReturn().getResponse();
    }

    @Test
    void healthIsPublicAndRevealsOnlyStatus() throws Exception {
        var res = fetch("/actuator/health");
        assertEquals(200, res.getStatus());
        String body = res.getContentAsString();
        assertTrue(body.contains("\"status\":\"UP\""), body);
        for (String leak : List.of("diskSpace", "\"db\"", "components", "details", "database", "H2",
                "PostgreSQL", "total", "free")) {
            assertFalse(body.contains(leak), leak + " -> " + body);
        }
    }

    @Test
    void livenessAndReadinessProbesWork() throws Exception {
        for (String url : List.of("/actuator/health/liveness", "/actuator/health/readiness")) {
            var res = fetch(url);
            assertEquals(200, res.getStatus(), url);
            assertTrue(res.getContentAsString().contains("\"status\":\"UP\""), url);
        }
    }

    @Test
    void otherActuatorEndpointsAreClosedForEveryone() throws Exception {
        for (String path : List.of("/actuator", "/actuator/env", "/actuator/beans", "/actuator/heapdump",
                "/actuator/loggers", "/actuator/metrics", "/actuator/info", "/actuator/threaddump")) {
            assertEquals(401, fetch(path).getStatus(), path);
            assertEquals(403, mvc.perform(get(path).with(user("b").roles("BARBER")))
                    .andReturn().getResponse().getStatus(), path);
            assertEquals(403, mvc.perform(get(path).with(user("a").roles("ADMIN")))
                    .andReturn().getResponse().getStatus(), path);
        }
    }

    @Test
    void healthResponseCarriesSecurityHeaders() throws Exception {
        var res = fetch("/actuator/health");
        assertEquals("DENY", res.getHeader("X-Frame-Options"));
        assertNotNull(res.getHeader("Content-Security-Policy"));
        assertEquals("nosniff", res.getHeader("X-Content-Type-Options"));
    }

    @Test
    void healthIsRateLimitedPerIp() throws Exception {
        RateLimitFilter f = new RateLimitFilter(new RateLimiter(true));
        for (int i = 0; i < 120; i++) {
            assertEquals(200, call(f, "198.51.100.5"));
        }
        assertEquals(429, call(f, "198.51.100.5"));
        assertEquals(200, call(f, "198.51.100.6"));
    }

    private int call(RateLimitFilter f, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, new MockFilterChain());
        return res.getStatus();
    }
}
