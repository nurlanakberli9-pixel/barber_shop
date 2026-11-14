package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDate;
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
class PanelTests {

    @Autowired
    WebApplicationContext ctx;
    @Autowired
    AppUserRepository users;
    @Autowired
    BarbershopRepository shops;
    @Autowired
    SlotRepository slots;
    @Autowired
    PasswordEncoder encoder;

    MockMvc mvc;
    Barbershop shopA;
    Barbershop shopB;
    LocalDate day;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
        shopA = shops.save(new Barbershop("Salon A", "Alpha", "A küç. 1", 4.0));
        shopB = shops.save(new Barbershop("Salon B", "Beta", "B küç. 2", 4.0));
        users.save(new AppUser("alpha", encoder.encode("x-Pass-123"), "BARBER", shopA));
        users.save(new AppUser("beta", encoder.encode("x-Pass-123"), "BARBER", shopB));
        day = LocalDate.now().plusDays(1);
    }

    private String body(LocalDate d, int from, int to) {
        return String.format("{\"date\":\"%s\",\"fromHour\":%d,\"toHour\":%d}", d, from, to);
    }

    private MockHttpServletResponse panelPost(String who, String url, String json)
            throws Exception {
        var b = post(url).with(user(who).roles("BARBER")).with(csrf());
        if (json != null) b.contentType(MediaType.APPLICATION_JSON).content(json);
        return mvc.perform(b).andReturn().getResponse();
    }

    private MockHttpServletResponse panelDelete(String who, String url) throws Exception {
        return mvc.perform(delete(url).with(user(who).roles("BARBER")).with(csrf()))
                .andReturn().getResponse();
    }

    private MockHttpServletResponse panelGet(String who, String url) throws Exception {
        return mvc.perform(get(url).with(user(who).roles("BARBER"))).andReturn().getResponse();
    }

    private int publicBook(Long slotId, String name, String phone) throws Exception {
        return mvc.perform(post("/api/slots/" + slotId + "/book")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"phone\":\"" + phone + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    private Slot slotAt(Barbershop shop, int hour) {
        return slots.findByBarbershopIdAndStartTimeBetweenOrderByStartTime(
                        shop.getId(), day.atStartOfDay(), day.plusDays(1).atStartOfDay())
                .stream().filter(s -> s.getStartTime().getHour() == hour)
                .findFirst().orElseThrow();
    }

    @Test
    void panelDayWithoutLoginIs401() throws Exception {
        assertEquals(401,
                mvc.perform(get("/api/panel/day?date=" + day)).andReturn().getResponse().getStatus());
    }

    @Test
    void createWithoutCsrfTokenIs403() throws Exception {
        int status = mvc.perform(post("/api/panel/slots").with(user("alpha").roles("BARBER"))
                .contentType(MediaType.APPLICATION_JSON).content(body(day, 10, 12)))
                .andReturn().getResponse().getStatus();
        assertEquals(403, status);
    }

    @Test
    void createsHourlySlotsAndSkipsExisting() throws Exception {
        var r1 = panelPost("alpha", "/api/panel/slots", body(day, 10, 13));
        assertEquals(200, r1.getStatus());
        assertEquals("{\"created\":3,\"skipped\":0}", r1.getContentAsString());
        var r2 = panelPost("alpha", "/api/panel/slots", body(day, 10, 13));
        assertEquals("{\"created\":0,\"skipped\":3}", r2.getContentAsString());
    }

    @Test
    void invalidCreateRequestsAre400() throws Exception {
        String[] bad = {
            body(day, 13, 13),
            body(day, 5, 12),
            body(day, 10, 22),
            body(LocalDate.now().minusDays(1), 10, 12),
            body(LocalDate.now().plusDays(60), 10, 12),
            "{\"fromHour\":10,\"toHour\":12}",
            "{bu json deyil"
        };
        for (String json : bad) {
            assertEquals(400, panelPost("alpha", "/api/panel/slots", json).getStatus(), json);
        }
        assertEquals(400, panelPost("alpha", "/api/panel/slots", null).getStatus());
    }

    @Test
    void ownerSeesCustomerDetailsButPublicApiDoesNot() throws Exception {
        panelPost("alpha", "/api/panel/slots", body(day, 10, 11));
        Slot s = slotAt(shopA, 10);
        assertEquals(200, publicBook(s.getId(), "Musteri", "+994551112233"));
        String own = panelGet("alpha", "/api/panel/day?date=" + day).getContentAsString();
        assertTrue(own.contains("Musteri"));
        assertTrue(own.contains("+994551112233"));
        assertTrue(own.contains("\"BOOKED\""));
        String pub = mvc.perform(get("/api/shops/" + shopA.getId() + "/day?date=" + day))
                .andReturn().getResponse().getContentAsString();
        assertTrue(pub.contains("\"BOOKED\""));
        assertFalse(pub.contains("Musteri"));
        assertFalse(pub.contains("+994551112233"));
    }

    @Test
    void otherBarberSeesNothingOfForeignShop() throws Exception {
        panelPost("alpha", "/api/panel/slots", body(day, 10, 12));
        assertEquals("[]", panelGet("beta", "/api/panel/day?date=" + day).getContentAsString());
        assertTrue(panelGet("alpha", "/api/panel/day?date=" + day)
                .getContentAsString().contains("\"FREE\""));
    }

    @Test
    void foreignOrUnknownSlotIs404() throws Exception {
        panelPost("alpha", "/api/panel/slots", body(day, 10, 11));
        Long id = slotAt(shopA, 10).getId();
        assertEquals(404, panelDelete("beta", "/api/panel/slots/" + id).getStatus());
        assertEquals(404, panelPost("beta", "/api/panel/slots/" + id + "/cancel", null).getStatus());
        assertEquals(404, panelDelete("alpha", "/api/panel/slots/999999999").getStatus());
        assertTrue(slots.findById(id).isPresent());
    }

    @Test
    void bookedSlotCannotBeDeletedButCanBeCancelledAndRebooked() throws Exception {
        panelPost("alpha", "/api/panel/slots", body(day, 10, 11));
        Long id = slotAt(shopA, 10).getId();
        assertEquals(200, publicBook(id, "Musteri", "+994551112233"));
        assertEquals(409, panelDelete("alpha", "/api/panel/slots/" + id).getStatus());
        assertEquals(204, panelPost("alpha", "/api/panel/slots/" + id + "/cancel", null).getStatus());
        assertEquals(409, panelPost("alpha", "/api/panel/slots/" + id + "/cancel", null).getStatus());
        assertEquals(200, publicBook(id, "Yeni", "+994551119999"));
    }

    @Test
    void freeSlotCanBeDeleted() throws Exception {
        panelPost("alpha", "/api/panel/slots", body(day, 10, 11));
        Long id = slotAt(shopA, 10).getId();
        assertEquals(204, panelDelete("alpha", "/api/panel/slots/" + id).getStatus());
        assertTrue(slots.findById(id).isEmpty());
    }
}
