package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Transactional
class PushTests {

    private static final String P256 = "BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QTpQtUbVlUls0VJXg7A8u-Ts1XbjhazAkj7I99e8QcYP7DkM";
    private static final String AUTH = "tBHItJI5svbpez7KI4CCXg";
    private static final String SUB = "/api/panel/push/subscribe";
    private static final String UNSUB = "/api/panel/push/unsubscribe";

    @Autowired
    WebApplicationContext ctx;
    @Autowired
    BarbershopRepository shops;
    @Autowired
    AppUserRepository users;
    @Autowired
    PushSubscriptionRepository subs;
    @Autowired
    PasswordEncoder encoder;

    MockMvc mvc;
    Barbershop shopA;
    Barbershop shopB;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
        shopA = shops.save(new Barbershop("Push A", "Alpha", "A kuc. 1", 4.0));
        shopB = shops.save(new Barbershop("Push B", "Beta", "B kuc. 2", 4.0));
        users.save(new AppUser("psha", encoder.encode("x-Pass-123"), "BARBER", shopA));
        users.save(new AppUser("pshb", encoder.encode("x-Pass-123"), "BARBER", shopB));
    }

    private String json(String endpoint, String p256dh, String auth) {
        return "{\"endpoint\":\"" + endpoint + "\",\"p256dh\":\"" + p256dh + "\",\"auth\":\"" + auth + "\"}";
    }

    private MockHttpServletResponse push(String who, String url, String body) throws Exception {
        var b = post(url).with(user(who).roles("BARBER")).with(csrf());
        if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(b).andReturn().getResponse();
    }

    private int subscribe(String who, String endpoint) throws Exception {
        return push(who, SUB, json(endpoint, P256, AUTH)).getStatus();
    }

    @Test
    void pushEndpointsNeedLogin() throws Exception {
        assertEquals(401, mvc.perform(get("/api/panel/push/key")).andReturn().getResponse().getStatus());
        assertEquals(401, mvc.perform(post(SUB).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json("https://fcm.googleapis.com/fcm/send/x", P256, AUTH)))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void keyEndpointReturnsTheVapidPublicKey() throws Exception {
        String body = mvc.perform(get("/api/panel/push/key").with(user("psha").roles("BARBER")))
                .andReturn().getResponse().getContentAsString();
        Matcher m = Pattern.compile("\"publicKey\":\"([A-Za-z0-9_-]+)\"").matcher(body);
        assertTrue(m.find(), body);
        assertEquals(87, m.group(1).length());
        assertTrue(body.contains("\"ephemeral\":"), body);
        assertFalse(body.toLowerCase().contains("private"), body);
    }

    @Test
    void subscribeAcceptsKnownPushServicesOnly() throws Exception {
        String[] good = {
            "https://fcm.googleapis.com/fcm/send/good1",
            "https://updates.push.services.mozilla.com/wpush/v2/good2",
            "https://web.push.apple.com/good3",
            "https://wns2-par02p.notify.windows.com/w/?token=good4"
        };
        for (String e : good) {
            assertEquals(204, subscribe("psha", e), e);
        }
        String[] bad = {
            "http://fcm.googleapis.com/fcm/send/x",
            "https://example.com/push/x",
            "https://evil.com/?u=fcm.googleapis.com",
            "https://fcm.googleapis.com.evil.com/x",
            "https://localhost/x",
            "https://127.0.0.1/x",
            "https://fcm.googleapis.com:8443/x",
            "https://user@fcm.googleapis.com/x",
            "ftp://fcm.googleapis.com/x",
            "javascript:alert(1)",
            "https://fcm.googleapis.com/" + "a".repeat(600)
        };
        for (String e : bad) {
            assertEquals(400, subscribe("psha", e), e);
        }
        String ok = "https://fcm.googleapis.com/fcm/send/keys";
        assertEquals(400, push("psha", SUB, json(ok, "!!!", AUTH)).getStatus());
        assertEquals(400, push("psha", SUB, json(ok, P256, "bad key")).getStatus());
        assertEquals(400, push("psha", SUB, json(ok, "", AUTH)).getStatus());
        assertEquals(400, push("psha", SUB, "{\"endpoint\":\"" + ok + "\"}").getStatus());
        assertEquals(400, push("psha", SUB, "{bu json deyil").getStatus());
        assertEquals(4, subs.findByBarbershopId(shopA.getId()).size());
    }

    @Test
    void resubscribingMovesTheDeviceToTheLatestBarber() throws Exception {
        String endpoint = "https://fcm.googleapis.com/fcm/send/shared";
        assertEquals(204, subscribe("psha", endpoint));
        assertEquals(shopA.getId(), subs.findByEndpoint(endpoint).orElseThrow().getBarbershop().getId());
        assertEquals(204, subscribe("pshb", endpoint));
        assertEquals(shopB.getId(), subs.findByEndpoint(endpoint).orElseThrow().getBarbershop().getId());
        assertTrue(subs.findByBarbershopId(shopA.getId()).isEmpty());
        assertEquals(1, subs.findByBarbershopId(shopB.getId()).size());
    }

    @Test
    void unsubscribeOnlyRemovesTheOwnDevice() throws Exception {
        String endpoint = "https://fcm.googleapis.com/fcm/send/mine";
        assertEquals(204, subscribe("psha", endpoint));
        String body = "{\"endpoint\":\"" + endpoint + "\"}";
        assertEquals(204, push("pshb", UNSUB, body).getStatus());
        assertTrue(subs.findByEndpoint(endpoint).isPresent());
        assertEquals(204, push("psha", UNSUB, body).getStatus());
        assertTrue(subs.findByEndpoint(endpoint).isEmpty());
    }

    @Test
    void testPushWithoutADeviceIs409() throws Exception {
        assertEquals(409, push("psha", "/api/panel/push/test", null).getStatus());
    }

    @Test
    void deviceLimitPerBarberIs409ButExistingDevicesStillUpdate() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertEquals(204, subscribe("psha", "https://fcm.googleapis.com/fcm/send/dev" + i));
        }
        assertEquals(409, subscribe("psha", "https://fcm.googleapis.com/fcm/send/dev10"));
        assertEquals(204, subscribe("psha", "https://fcm.googleapis.com/fcm/send/dev3"));
        assertEquals(10, subs.findByBarbershopId(shopA.getId()).size());
    }

    @Test
    void endpointAllowListRules() {
        for (String e : List.of(
                "https://fcm.googleapis.com/fcm/send/abc",
                "https://fcm.googleapis.com:443/fcm/send/abc",
                "https://updates.push.services.mozilla.com/wpush/v2/abc",
                "https://web.push.apple.com/abc",
                "https://wns2-par02p.notify.windows.com/w/?token=abc")) {
            assertTrue(PushEndpoints.allowed(e), e);
        }
        for (String e : List.of(
                "http://fcm.googleapis.com/x",
                "https://evilfcm.googleapis.com/x",
                "https://fcm.googleapis.com.evil.com/x",
                "https://[::1]/x",
                "https://user@fcm.googleapis.com/x",
                "")) {
            assertFalse(PushEndpoints.allowed(e), e);
        }
        assertFalse(PushEndpoints.allowed(null));
    }
}
