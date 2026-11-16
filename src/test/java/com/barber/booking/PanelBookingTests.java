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
import java.util.ArrayList;
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
class PanelBookingTests {

    private static final String PHONE = "+994501234567";
    private static final String H = "[\"HAIRCUT\"]";
    private static final String KEEP_FIRST =
            "{\"windows\":[{\"startTime\":\"10:00\",\"cubes\":[\"HAIRCUT\",\"WASH\",\"BEARD\"]}]}";
    private static final String MOVED =
            "{\"windows\":[{\"startTime\":\"12:00\",\"cubes\":[\"HAIRCUT\"]}]}";

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
    Barbershop shopA;
    LocalDate day;

    private static int minutesOf(ServiceKind k) {
        return k == ServiceKind.HAIRCUT ? 30 : k == ServiceKind.WASH ? 15 : 20;
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
        shopA = shops.save(new Barbershop("PB A", "Alpha", "A küç. 1", 4.0));
        Barbershop shopB = shops.save(new Barbershop("PB B", "Beta", "B küç. 2", 4.0));
        for (ServiceKind k : ServiceKind.values()) {
            ServiceType t = types.save(new ServiceType(shopA, k));
            t.setDurationMinutes(minutesOf(k));
        }
        users.save(new AppUser("pba", encoder.encode("x-Pass-123"), "BARBER", shopA));
        users.save(new AppUser("pbb", encoder.encode("x-Pass-123"), "BARBER", shopB));
        day = LocalDate.now().plusDays(1);
    }

    private Long window(String start, ServiceKind... kinds) {
        WorkWindow w = new WorkWindow(shopA, day, LocalTime.parse(start));
        for (ServiceKind k : kinds) w.addCube(k, minutesOf(k));
        return windows.save(w).getId();
    }

    private Long allThree() {
        return window("10:00", ServiceKind.HAIRCUT, ServiceKind.WASH, ServiceKind.BEARD);
    }

    private MockHttpServletResponse bookPublic(Long windowId, String services, String name)
            throws Exception {
        return mvc.perform(post("/api/shops/" + shopA.getId() + "/windows/" + windowId + "/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"services\":" + services + ",\"name\":\"" + name
                        + "\",\"phone\":\"" + PHONE + "\"}"))
                .andReturn().getResponse();
    }

    private MockHttpServletResponse panelGet(String who, String url) throws Exception {
        return mvc.perform(get(url).with(user(who).roles("BARBER"))).andReturn().getResponse();
    }

    private String list(String who) throws Exception {
        return panelGet(who, "/api/panel/bookings?date=" + day).getContentAsString();
    }

    private MockHttpServletResponse cancel(String who, Object id) throws Exception {
        return mvc.perform(post("/api/panel/bookings/" + id + "/cancel")
                .with(user(who).roles("BARBER")).with(csrf())).andReturn().getResponse();
    }

    private MockHttpServletResponse putWindows(String who, String extra, String json)
            throws Exception {
        return mvc.perform(put("/api/panel/windows?date=" + day + extra)
                .with(user(who).roles("BARBER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn().getResponse();
    }

    private List<Long> ids(String body) {
        List<Long> out = new ArrayList<>();
        Matcher m = Pattern.compile("\"id\":(\\d+)").matcher(body);
        while (m.find()) out.add(Long.parseLong(m.group(1)));
        return out;
    }

    @Test
    void listWithoutLoginIs401() throws Exception {
        assertEquals(401, mvc.perform(get("/api/panel/bookings?date=" + day))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void barberSeesOwnBookingsWithContactButOtherBarberDoesNot() throws Exception {
        Long w = allThree();
        assertEquals(200, bookPublic(w, "[\"HAIRCUT\",\"WASH\"]", "Elvin").getStatus());
        String own = list("pba");
        assertTrue(own.contains("\"customerName\":\"Elvin\""), own);
        assertTrue(own.contains("\"customerPhone\":\"" + PHONE + "\""), own);
        assertTrue(own.contains("\"start\":\"10:00\",\"end\":\"10:45\""), own);
        assertTrue(own.contains("\"code\":\"HAIRCUT\""), own);
        assertTrue(own.contains("\"code\":\"WASH\""), own);
        assertEquals("[]", list("pbb"));
    }

    @Test
    void invalidDatesAre400() throws Exception {
        assertEquals(400, panelGet("pba", "/api/panel/bookings").getStatus());
        assertEquals(400, panelGet("pba",
                "/api/panel/bookings?date=" + LocalDate.now().minusDays(1)).getStatus());
        assertEquals(400, panelGet("pba",
                "/api/panel/bookings?date=" + LocalDate.now().plusDays(60)).getStatus());
    }

    @Test
    void cancelFreesTheTimeAgain() throws Exception {
        Long w = allThree();
        assertEquals(200, bookPublic(w, H, "Elvin").getStatus());
        long id = ids(list("pba")).get(0);
        assertEquals(204, cancel("pba", id).getStatus());
        assertEquals("[]", list("pba"));
        String view = mvc.perform(get("/api/shops/" + shopA.getId() + "/windows?date=" + day))
                .andReturn().getResponse().getContentAsString();
        assertTrue(view.contains("\"freeMinutes\":65,\"fitMinutes\":65"), view);
        assertEquals(404, cancel("pba", id).getStatus());
    }

    @Test
    void foreignOrUnknownCancelIs404AndBookingStays() throws Exception {
        Long w = allThree();
        assertEquals(200, bookPublic(w, H, "Elvin").getStatus());
        long id = ids(list("pba")).get(0);
        assertEquals(404, cancel("pbb", id).getStatus());
        assertEquals(404, cancel("pba", 999999).getStatus());
        assertTrue(list("pba").contains("Elvin"));
    }

    @Test
    void cancelWithoutCsrfTokenIs403() throws Exception {
        Long w = allThree();
        assertEquals(200, bookPublic(w, H, "Elvin").getStatus());
        long id = ids(list("pba")).get(0);
        int status = mvc.perform(post("/api/panel/bookings/" + id + "/cancel")
                .with(user("pba").roles("BARBER"))).andReturn().getResponse().getStatus();
        assertEquals(403, status);
        assertTrue(list("pba").contains("Elvin"));
    }

    @Test
    void strandedBookingBlocksWindowChangeUnlessListedThenItIsCancelled() throws Exception {
        Long w = allThree();
        assertEquals(200, bookPublic(w, H, "Elvin").getStatus());
        long id = ids(list("pba")).get(0);
        assertEquals(409, putWindows("pba", "", MOVED).getStatus());
        assertEquals(409, putWindows("pba", "&cancelBookingIds=999999", MOVED).getStatus());
        assertTrue(list("pba").contains("Elvin"));
        assertEquals(200, putWindows("pba", "&cancelBookingIds=" + id, MOVED).getStatus());
        assertEquals("[]", list("pba"));
    }

    @Test
    void onlyStrandedBookingsAreCancelled() throws Exception {
        Long w1 = allThree();
        Long w2 = window("12:00", ServiceKind.HAIRCUT);
        assertEquals(200, bookPublic(w1, H, "Elvin").getStatus());
        assertEquals(200, bookPublic(w2, H, "Murad").getStatus());
        List<Long> ids = ids(list("pba"));
        assertEquals(2, ids.size());
        assertEquals(200, putWindows("pba", "&cancelBookingIds=" + ids.get(0) + "," + ids.get(1),
                KEEP_FIRST).getStatus());
        String after = list("pba");
        assertTrue(after.contains("Elvin"), after);
        assertFalse(after.contains("Murad"), after);
    }
}
