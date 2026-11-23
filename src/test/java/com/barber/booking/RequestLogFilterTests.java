package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLogFilterTests {

    private static final String UUID_RE = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attach() {
        logger = (Logger) LoggerFactory.getLogger(RequestLogFilter.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
        logger.setLevel(null);
    }

    private MockHttpServletResponse run(String method, String uri, String query, String requestId,
            int status) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRemoteAddr("203.0.113.9");
        if (query != null) {
            req.setQueryString(query);
            req.setParameter("phone", "+994501234567");
        }
        if (requestId != null) req.addHeader("X-Request-Id", requestId);
        req.setContent("{\"phone\":\"+994501234567\",\"name\":\"Elvin\"}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse res = new MockHttpServletResponse();
        new RequestLogFilter().doFilter(req, res, (rq, rs) -> ((HttpServletResponse) rs).setStatus(status));
        return res;
    }

    @Test
    void logsMethodPathAndStatusButNeverQueryBodyOrRawIp() throws Exception {
        run("POST", "/api/shops/1/bookings", "phone=%2B994501234567&name=Elvin", null, 200);
        assertEquals(1, appender.list.size());
        ILoggingEvent e = appender.list.get(0);
        String msg = e.getFormattedMessage();
        assertEquals(Level.INFO, e.getLevel());
        assertTrue(msg.contains("method=POST"), msg);
        assertTrue(msg.contains("path=/api/shops/1/bookings"), msg);
        assertTrue(msg.contains("status=200"), msg);
        assertTrue(msg.matches(".*ms=\\d+.*"), msg);
        assertTrue(msg.matches(".*ip=[0-9a-f]{8} .*"), msg);
        for (String leak : new String[] {"994", "Elvin", "phone", "203.0.113.9"}) {
            assertFalse(msg.contains(leak), leak + " -> " + msg);
        }
    }

    @Test
    void generatesRequestIdAndOnlyReusesWellFormedClientIds() throws Exception {
        var generated = run("GET", "/api/shops", null, null, 200);
        String id = generated.getHeader("X-Request-Id");
        assertNotNull(id);
        assertTrue(id.matches(UUID_RE), id);
        assertTrue(appender.list.get(0).getFormattedMessage().contains("rid=" + id));

        var reused = run("GET", "/api/shops", null, "client-id-12345", 200);
        assertEquals("client-id-12345", reused.getHeader("X-Request-Id"));

        for (String bad : new String[] {"abc", "bad id\r\nX-Injected: 1", "a".repeat(65)}) {
            String replaced = run("GET", "/api/shops", null, bad, 200).getHeader("X-Request-Id");
            assertNotEquals(bad, replaced);
            assertTrue(replaced.matches(UUID_RE), replaced);
        }
    }

    @Test
    void clearsMdcAndSkipsNonApiPaths() throws Exception {
        run("GET", "/api/shops", null, null, 200);
        assertNull(MDC.get("requestId"));
        appender.list.clear();
        var res = run("GET", "/index.html", null, null, 200);
        assertTrue(appender.list.isEmpty());
        assertNull(res.getHeader("X-Request-Id"));
    }

    @Test
    void securityRelevantAndFailedRequestsUseHigherLevels() throws Exception {
        run("POST", "/api/shops/1/bookings", null, null, 429);
        run("GET", "/api/panel/me", null, null, 403);
        run("GET", "/api/shops/1/day", null, null, 500);
        run("GET", "/api/panel/me", null, null, 401);
        run("POST", "/api/shops/1/bookings", null, null, 400);
        assertEquals(Level.WARN, appender.list.get(0).getLevel());
        assertEquals(Level.WARN, appender.list.get(1).getLevel());
        assertEquals(Level.ERROR, appender.list.get(2).getLevel());
        assertEquals(Level.INFO, appender.list.get(3).getLevel());
        assertEquals(Level.INFO, appender.list.get(4).getLevel());
    }
}
