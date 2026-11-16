package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.LocalDate;
import java.time.LocalTime;
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
class ScheduleTests {

    private static final String NAME = "Elvin";
    private static final String PHONE = "+994501234567";
    private static final String H = "[\"HAIRCUT\"]";

    @Autowired
    WebApplicationContext ctx;
    @Autowired
    BarbershopRepository shops;
    @Autowired
    ServiceTypeRepository types;
    @Autowired
    WorkWindowRepository windows;
    @Autowired
    AppUserRepository users;
    @Autowired
    PasswordEncoder encoder;

    MockMvc mvc;
    Barbershop shop;
    Barbershop bare;
    LocalDate day;

    private static int minutesOf(ServiceKind k) {
        return k == ServiceKind.HAIRCUT ? 30 : k == ServiceKind.WASH ? 15 : 20;
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
        shop = shops.save(new Barbershop("Sch A", "Alpha", "A küç. 1", 4.0));
        bare = shops.save(new Barbershop("Sch B", "Beta", "B küç. 2", 4.0));
        for (ServiceKind k : ServiceKind.values()) {
            ServiceType t = types.save(new ServiceType(shop, k));
            t.setDurationMinutes(minutesOf(k));
        }
        users.save(new AppUser("scha", encoder.encode("x-Pass-123"), "BARBER", shop));
        day = LocalDate.now().plusDays(1);
    }

    private void window(String start, ServiceKind... kinds) {
        WorkWindow w = new WorkWindow(shop, day, LocalTime.parse(start));
        for (ServiceKind k : kinds) w.addCube(k, minutesOf(k));
        windows.save(w);
    }

    private void allThree(String start) {
        window(start, ServiceKind.HAIRCUT, ServiceKind.WASH, ServiceKind.BEARD);
    }

    private Long windowId(int index) {
        return windows.findByBarbershopIdAndWindowDateOrderByStartTime(shop.getId(), day)
                .get(index).getId();
    }

    private MockHttpServletResponse fetch(String url) throws Exception {
        return mvc.perform(get(url)).andReturn().getResponse();
    }

    private String listWindows() throws Exception {
        return fetch("/api/shops/" + shop.getId() + "/windows?date=" + day).getContentAsString();
    }

    private String gaps() throws Exception {
        return fetch("/api/shops/" + shop.getId() + "/gaps?date=" + day).getContentAsString();
    }

    private String body(String services, String name, String phone) {
        return String.format("{\"services\":%s,\"name\":\"%s\",\"phone\":\"%s\"}",
                services, name, phone);
    }

    private MockHttpServletResponse book(Long shopId, Object windowId, String body)
            throws Exception {
        return mvc.perform(post("/api/shops/" + shopId + "/windows/" + windowId + "/bookings")
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse();
    }

    private MockHttpServletResponse ok(int windowIndex, String services) throws Exception {
        return book(shop.getId(), windowId(windowIndex), body(services, NAME, PHONE));
    }

    private int count(String text, String part) {
        return text.split(Pattern.quote(part), -1).length - 1;
    }

    @Test
    void windowsListShowsCubesAndFreeTime() throws Exception {
        allThree("10:00");
        String body = listWindows();
        assertTrue(body.contains("\"start\":\"10:00\",\"end\":\"11:05\",\"totalMinutes\":65,"
                + "\"freeMinutes\":65,\"fitMinutes\":65"), body);
        assertEquals(3, count(body, "\"code\""), body);
        assertTrue(body.contains("\"minutes\":30"), body);
        assertTrue(body.contains("\"minutes\":15"), body);
        assertTrue(body.contains("\"minutes\":20"), body);
        assertEquals("[]", fetch("/api/shops/" + bare.getId() + "/windows?date=" + day)
                .getContentAsString());
    }

    @Test
    void bookingTakesEarliestFreeSpotInsideTheWindow() throws Exception {
        allThree("10:00");
        var first = ok(0, "[\"HAIRCUT\",\"WASH\"]");
        assertEquals(200, first.getStatus());
        String fb = first.getContentAsString();
        assertTrue(fb.contains("\"start\":\"10:00\",\"end\":\"10:45\",\"minutes\":45"), fb);
        assertFalse(fb.contains(PHONE));
        assertTrue(listWindows().contains("\"freeMinutes\":20,\"fitMinutes\":20"));

        var second = ok(0, "[\"BEARD\"]");
        assertEquals(200, second.getStatus());
        assertTrue(second.getContentAsString()
                .contains("\"start\":\"10:45\",\"end\":\"11:05\",\"minutes\":20"));
        String full = listWindows();
        assertTrue(full.contains("\"freeMinutes\":0,\"fitMinutes\":0"), full);
        assertEquals(409, ok(0, "[\"WASH\"]").getStatus());
        assertFalse(full.contains(NAME));
        assertFalse(full.contains(PHONE));
    }

    @Test
    void leftoverMergesWithTheNextUntouchedWindow() throws Exception {
        allThree("10:00");
        window("11:05", ServiceKind.HAIRCUT);
        assertEquals(200, ok(0, H).getStatus());
        String g = gaps();
        assertEquals(1, count(g, "\"minutes\""), g);
        assertTrue(g.contains("\"start\":\"10:30\",\"end\":\"11:35\",\"minutes\":65"), g);
        assertTrue(listWindows().contains("\"freeMinutes\":35,\"fitMinutes\":35"));
    }

    @Test
    void separatedWindowsGiveSeparateGaps() throws Exception {
        window("10:00", ServiceKind.HAIRCUT);
        window("12:00", ServiceKind.HAIRCUT);
        assertEquals(2, count(gaps(), "\"minutes\""));
    }

    @Test
    void cubeMissingFromWindowOrNoRoomIs409() throws Exception {
        window("10:00", ServiceKind.WASH, ServiceKind.BEARD);
        window("12:00", ServiceKind.HAIRCUT, ServiceKind.WASH);
        assertEquals(409, ok(0, H).getStatus());
        assertEquals(200, ok(1, "[\"HAIRCUT\",\"WASH\"]").getStatus());
        assertEquals(409, ok(1, H).getStatus());
    }

    @Test
    void invalidBookingRequestsAre400() throws Exception {
        allThree("10:00");
        Long wid = windowId(0);
        String[] bad = {
            body(H, "", PHONE),
            body(H, "A".repeat(61), PHONE),
            body(H, NAME, "123"),
            body("[]", NAME, PHONE),
            body("[\"HAIRCUT\",\"HAIRCUT\"]", NAME, PHONE),
            body("[\"COLOR\"]", NAME, PHONE),
            body("[\"HAIRCUT\",\"WASH\",\"BEARD\",\"HAIRCUT\"]", NAME, PHONE),
            "{\"name\":\"Elvin\",\"phone\":\"+994501234567\"}",
            "{bu json deyil"
        };
        for (String json : bad) {
            assertEquals(400, book(shop.getId(), wid, json).getStatus(), json);
        }
        assertEquals(400, book(shop.getId(), "abc", body(H, NAME, PHONE)).getStatus());
    }

    @Test
    void unknownShopOrWindowIs404() throws Exception {
        allThree("10:00");
        Long wid = windowId(0);
        String okBody = body(H, NAME, PHONE);
        assertEquals(404, book(999999L, wid, okBody).getStatus());
        assertEquals(404, book(shop.getId(), 999999L, okBody).getStatus());
        assertEquals(404, book(bare.getId(), wid, okBody).getStatus());
        assertEquals(404, fetch("/api/shops/999999/windows?date=" + day).getStatus());
        assertEquals(404, fetch("/api/shops/999999/gaps?date=" + day).getStatus());
        assertEquals(400, fetch("/api/shops/" + shop.getId() + "/windows").getStatus());
        assertEquals(400, fetch("/api/shops/" + shop.getId() + "/windows?date="
                + LocalDate.now().plusDays(60)).getStatus());
    }

    @Test
    void windowChangeCannotStrandABooking() throws Exception {
        allThree("10:00");
        assertEquals(200, ok(0, H).getStatus());
        assertEquals(409, putWindows("{\"windows\":[]}"));
        assertEquals(409, putWindows(
                "{\"windows\":[{\"startTime\":\"12:00\",\"cubes\":[\"HAIRCUT\"]}]}"));
        assertEquals(200, putWindows("{\"windows\":[{\"startTime\":\"09:30\","
                + "\"cubes\":[\"HAIRCUT\",\"WASH\",\"BEARD\"]}]}"));
    }

    private int putWindows(String json) throws Exception {
        return mvc.perform(put("/api/panel/windows?date=" + day)
                .with(user("scha").roles("BARBER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void calendarShowsWindowAndFreeMinutes() throws Exception {
        allThree("10:00");
        assertEquals(200, ok(0, H).getStatus());
        String body = fetch("/api/shops/" + shop.getId() + "/calendar?days=3").getContentAsString();
        assertEquals(3, count(body, "\"date\""), body);
        assertTrue(body.contains("\"date\":\"" + day + "\",\"windowMinutes\":65,"
                + "\"freeMinutes\":35"), body);
        String empty = fetch("/api/shops/" + bare.getId() + "/calendar?days=3").getContentAsString();
        assertEquals(3, count(empty, "\"windowMinutes\":0"), empty);
    }

    @Test
    void calendarIsCappedAt60Days() throws Exception {
        assertEquals(60, count(fetch("/api/shops/" + shop.getId() + "/calendar?days=999")
                .getContentAsString(), "\"date\""));
        assertEquals(1, count(fetch("/api/shops/" + shop.getId() + "/calendar?days=0")
                .getContentAsString(), "\"date\""));
    }
}
