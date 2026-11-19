package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
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
class AdminTests {

    private static final String PASS = "Strong-Pass-123";
    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    @Autowired
    WebApplicationContext ctx;
    @Autowired
    AppUserRepository users;
    @Autowired
    BarbershopRepository shops;
    @Autowired
    PasswordEncoder encoder;

    MockMvc mvc;
    String addr;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
        addr = "10.2.0." + NEXT_IP.getAndIncrement();
    }

    private String body(String username, String password, String shopName, String masterName,
            String address) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password
                + "\",\"shopName\":\"" + shopName + "\",\"masterName\":\"" + masterName
                + "\",\"address\":\"" + address + "\"}";
    }

    private MockHttpServletResponse adminPost(String url, String json) throws Exception {
        return mvc.perform(post(url).with(user("adm").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn().getResponse();
    }

    private MockHttpServletResponse adminGet(String url) throws Exception {
        return mvc.perform(get(url).with(user("adm").roles("ADMIN"))).andReturn().getResponse();
    }

    private int login(String username, String password) throws Exception {
        return mvc.perform(post("/api/login").param("username", username)
                .param("password", password).with(csrf())
                .with(request -> { request.setRemoteAddr(addr); return request; }))
                .andReturn().getResponse().getStatus();
    }

    private long idOf(String json) {
        Matcher m = Pattern.compile("\"id\":(\\d+)").matcher(json);
        assertTrue(m.find(), json);
        return Long.parseLong(m.group(1));
    }

    private String shops() throws Exception {
        return mvc.perform(get("/api/shops")).andReturn().getResponse().getContentAsString();
    }

    @Test
    void adminEndpointsRequireTheAdminRole() throws Exception {
        assertEquals(401, mvc.perform(get("/api/admin/barbers")).andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(get("/api/admin/barbers").with(user("b").roles("BARBER")))
                .andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(post("/api/admin/barbers").with(user("b").roles("BARBER"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body("hacker1", PASS, "S", "M", "A"))).andReturn().getResponse().getStatus());
        assertEquals(200, adminGet("/api/admin/barbers").getStatus());
        assertEquals(403, mvc.perform(get("/api/panel/bookings?date=" + LocalDate.now())
                .with(user("adm").roles("ADMIN"))).andReturn().getResponse().getStatus());
    }

    @Test
    void createBarberMakesShopAndWorkingLogin() throws Exception {
        var res = adminPost("/api/admin/barbers", body("newbarber", PASS, "New Salon", "Newman", "Yeni kuc. 5"));
        assertEquals(200, res.getStatus());
        String created = res.getContentAsString();
        assertTrue(created.contains("\"username\":\"newbarber\""), created);
        assertTrue(created.contains("\"enabled\":true"), created);
        assertFalse(created.toLowerCase().contains("password"), created);
        assertTrue(adminGet("/api/admin/barbers").getContentAsString().contains("newbarber"));
        assertTrue(shops().contains("Newman"));
        assertEquals(200, login("newbarber", PASS));
    }

    @Test
    void invalidCreateRequestsAre400() throws Exception {
        String[] bad = {
            body("ab", PASS, "S", "M", "A"),
            body("Bad", PASS, "S", "M", "A"),
            body("a b", PASS, "S", "M", "A"),
            body("a".repeat(33), PASS, "S", "M", "A"),
            body("okname1", "short", "S", "M", "A"),
            body("okname2", "p".repeat(73), "S", "M", "A"),
            body("okname3", "\u0259".repeat(40), "S", "M", "A"),
            body("okname4", PASS, "", "M", "A"),
            body("okname5", PASS, "S".repeat(81), "M", "A"),
            body("okname6", PASS, "S", "", "A"),
            body("okname7", PASS, "S", "M".repeat(61), "A"),
            body("okname8", PASS, "S", "M", ""),
            body("okname9", PASS, "S", "M", "A".repeat(121)),
            body("okname11", PASS, "   ", "M", "A"),
            "{\"username\":\"okname10\",\"password\":\"" + PASS + "\"}",
            "{bu json deyil"
        };
        for (String json : bad) {
            assertEquals(400, adminPost("/api/admin/barbers", json).getStatus(), json);
        }
        assertEquals(200, adminPost("/api/admin/barbers",
                body("goodname", PASS, "Good Salon", "Goodman", "Good kuc. 1")).getStatus());
    }

    @Test
    void duplicateUsernameIs409() throws Exception {
        assertEquals(200, adminPost("/api/admin/barbers",
                body("dupname", PASS, "Dup Salon", "Dupman", "Dup kuc. 1")).getStatus());
        assertEquals(409, adminPost("/api/admin/barbers",
                body("dupname", PASS, "Other Salon", "Otherman", "Other kuc. 2")).getStatus());
        assertEquals(409, adminPost("/api/admin/barbers",
                body("mia", PASS, "Other Salon", "Otherman", "Other kuc. 2")).getStatus());
    }

    @Test
    void resetPasswordChangesLogin() throws Exception {
        long id = idOf(adminPost("/api/admin/barbers",
                body("resetme", PASS, "Reset Salon", "Resetman", "Reset kuc. 1")).getContentAsString());
        String url = "/api/admin/barbers/" + id + "/password";
        assertEquals(400, adminPost(url, "{\"password\":\"short\"}").getStatus());
        assertEquals(204, adminPost(url, "{\"password\":\"Another-Pass-456\"}").getStatus());
        assertEquals(401, login("resetme", PASS));
        assertEquals(200, login("resetme", "Another-Pass-456"));
    }

    @Test
    void disableBlocksLoginAndHidesTheShopThenEnableRestoresIt() throws Exception {
        long id = idOf(adminPost("/api/admin/barbers",
                body("toggleme", PASS, "Toggle Salon", "Toggleman", "Toggle kuc. 1")).getContentAsString());
        Long shopId = shops.findAll().stream()
                .filter(s -> "Toggleman".equals(s.getMasterName())).findFirst().orElseThrow().getId();
        String bookingJson = "{\"date\":\"" + LocalDate.now().plusDays(1)
                + "\",\"startTime\":\"08:00\",\"endTime\":\"08:10\",\"services\":[{\"code\":\"WASH\"}],"
                + "\"name\":\"Elvin\",\"phone\":\"+994501234567\"}";
        String url = "/api/admin/barbers/" + id + "/enabled";

        assertEquals(204, adminPost(url, "{\"enabled\":false}").getStatus());
        assertEquals(401, login("toggleme", PASS));
        assertFalse(shops().contains("Toggleman"));
        assertEquals(404, mvc.perform(post("/api/shops/" + shopId + "/bookings")
                .contentType(MediaType.APPLICATION_JSON).content(bookingJson))
                .andReturn().getResponse().getStatus());
        assertEquals(404, mvc.perform(get("/api/shops/" + shopId + "/day?date=" + LocalDate.now().plusDays(1)))
                .andReturn().getResponse().getStatus());
        assertTrue(adminGet("/api/admin/barbers").getContentAsString().contains("\"enabled\":false"));

        assertEquals(204, adminPost(url, "{\"enabled\":true}").getStatus());
        assertEquals(200, login("toggleme", PASS));
        assertTrue(shops().contains("Toggleman"));
    }

    @Test
    void unknownOrNonBarberIdIs404() throws Exception {
        AppUser other = users.save(new AppUser("otheradmin", encoder.encode(PASS), "ADMIN", null));
        for (long id : new long[] {999999L, other.getId()}) {
            assertEquals(404, adminPost("/api/admin/barbers/" + id + "/password",
                    "{\"password\":\"Another-Pass-456\"}").getStatus());
            assertEquals(404, adminPost("/api/admin/barbers/" + id + "/enabled",
                    "{\"enabled\":false}").getStatus());
        }
    }
}
