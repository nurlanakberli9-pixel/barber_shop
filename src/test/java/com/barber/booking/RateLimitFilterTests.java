package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTests {

    private static final String BOOKING = "/api/shops/1/bookings";

    private MockHttpServletResponse send(RateLimitFilter f, String method, String uri, String ip,
            int bodyBytes) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRemoteAddr(ip);
        if (bodyBytes > 0) req.setContent(new byte[bodyBytes]);
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, new MockFilterChain());
        return res;
    }

    private int call(RateLimitFilter f, String method, String uri, String ip) throws Exception {
        return send(f, method, uri, ip, 0).getStatus();
    }

    private RateLimitFilter filter() {
        return new RateLimitFilter(new RateLimiter(true));
    }

    @Test
    void bookingsAreLimitedPerMinutePerIp() throws Exception {
        RateLimitFilter f = filter();
        for (int i = 0; i < 5; i++) assertEquals(200, call(f, "POST", BOOKING, "7.7.7.7"));
        var blocked = send(f, "POST", BOOKING, "7.7.7.7", 0);
        assertEquals(429, blocked.getStatus());
        assertNotNull(blocked.getHeader("Retry-After"));
        assertEquals(200, call(f, "POST", BOOKING, "7.7.7.8"));
    }

    @Test
    void loginIsLimitedPerIp() throws Exception {
        RateLimitFilter f = filter();
        for (int i = 0; i < 20; i++) assertEquals(200, call(f, "POST", "/api/login", "7.7.7.1"));
        assertEquals(429, call(f, "POST", "/api/login", "7.7.7.1"));
        assertEquals(200, call(f, "POST", "/api/login", "7.7.7.2"));
    }

    @Test
    void publicReadsHaveAGenerousLimit() throws Exception {
        RateLimitFilter f = filter();
        for (int i = 0; i < 240; i++) assertEquals(200, call(f, "GET", "/api/shops/1/day", "7.7.7.3"));
        assertEquals(429, call(f, "GET", "/api/shops/1/day", "7.7.7.3"));
    }

    @Test
    void staticFilesAndPanelReadsAreNeverLimited() throws Exception {
        RateLimitFilter f = filter();
        for (int i = 0; i < 300; i++) {
            assertEquals(200, call(f, "GET", "/index.html", "7.7.7.4"));
            assertEquals(200, call(f, "GET", "/js/panel.js", "7.7.7.4"));
            assertEquals(200, call(f, "GET", "/api/panel/bookings", "7.7.7.4"));
        }
    }

    @Test
    void oversizedApiBodiesAre413() throws Exception {
        RateLimitFilter f = filter();
        assertEquals(413, send(f, "POST", BOOKING, "7.7.7.5", 20_000).getStatus());
        assertEquals(200, send(f, "POST", BOOKING, "7.7.7.5", 100).getStatus());
    }
}
