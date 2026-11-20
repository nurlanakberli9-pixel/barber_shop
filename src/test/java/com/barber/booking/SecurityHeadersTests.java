package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class SecurityHeadersTests {

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

    private void assertStrictHeaders(MockHttpServletResponse res) {
        String csp = res.getHeader("Content-Security-Policy");
        assertNotNull(csp);
        assertTrue(csp.contains("default-src 'none'"), csp);
        assertTrue(csp.contains("script-src 'self'"), csp);
        assertTrue(csp.contains("style-src 'self'"), csp);
        assertTrue(csp.contains("frame-ancestors 'none'"), csp);
        assertTrue(csp.contains("base-uri 'none'"), csp);
        assertFalse(csp.contains("unsafe-inline"), csp);
        assertFalse(csp.contains("unsafe-eval"), csp);
        assertEquals("DENY", res.getHeader("X-Frame-Options"));
        assertEquals("no-referrer", res.getHeader("Referrer-Policy"));
        assertEquals("nosniff", res.getHeader("X-Content-Type-Options"));
        assertTrue(res.getHeader("Permissions-Policy").contains("camera=()"));
        assertEquals("same-origin", res.getHeader("Cross-Origin-Opener-Policy"));
    }

    @Test
    void pagesCarryStrictSecurityHeaders() throws Exception {
        var res = fetch("/index.html");
        assertEquals(200, res.getStatus());
        assertStrictHeaders(res);
    }

    @Test
    void apiAndDeniedResponsesCarryTheHeadersToo() throws Exception {
        assertStrictHeaders(fetch("/api/shops"));
        var denied = fetch("/api/hack");
        assertEquals(401, denied.getStatus());
        assertStrictHeaders(denied);
    }

    @Test
    void htmlPagesHaveNoInlineScriptsStylesOrHandlers() throws Exception {
        Pattern inlineScript = Pattern.compile("<script(?![^>]*\\bsrc=)");
        Pattern handler = Pattern.compile("\\son[a-z]+\\s*=");
        for (String n : new String[] {"index", "panel", "admin"}) {
            String html = fetch("/" + n + ".html").getContentAsString(StandardCharsets.UTF_8);
            assertFalse(inlineScript.matcher(html).find(), n);
            assertFalse(html.contains("<style"), n);
            assertFalse(html.contains(" style="), n);
            assertFalse(handler.matcher(html).find(), n);
            assertTrue(html.contains("<link rel=\"stylesheet\" href=\"/css/" + n + ".css\">"), n);
            assertTrue(html.contains("<script src=\"/js/" + n + ".js\"></script>"), n);
        }
    }

    @Test
    void corsIsClosedForForeignOrigins() throws Exception {
        var res = mvc.perform(get("/api/shops").header("Origin", "https://evil.example"))
                .andReturn().getResponse();
        assertNull(res.getHeader("Access-Control-Allow-Origin"));
        var pre = mvc.perform(options("/api/shops/1/bookings")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST")).andReturn().getResponse();
        assertNull(pre.getHeader("Access-Control-Allow-Origin"));
    }

    @Test
    void staticAssetsAreServedPublicly() throws Exception {
        for (String n : new String[] {"index", "panel", "admin"}) {
            for (String path : new String[] {"/css/" + n + ".css", "/js/" + n + ".js"}) {
                var res = fetch(path);
                assertEquals(200, res.getStatus(), path);
                assertTrue(res.getContentAsString(StandardCharsets.UTF_8).length() > 100, path);
            }
        }
        assertEquals(404, fetch("/js/nope.js").getStatus());
    }
}
