package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
class BookingFlowTests {

    private static final String NAME = "Elvin";
    private static final String PHONE = "+994501234567";
    private static final String H_LOW = "[{\"code\":\"HAIRCUT\",\"level\":\"LOW\"}]";
    private static final String H_MED = "[{\"code\":\"HAIRCUT\",\"level\":\"MEDIUM\"}]";
    private static final String H_HIGH = "[{\"code\":\"HAIRCUT\",\"level\":\"HIGH\"}]";
    private static final String W = "[{\"code\":\"WASH\"}]";
    private static final String HW = "[{\"code\":\"HAIRCUT\",\"level\":\"MEDIUM\"},{\"code\":\"WASH\"}]";

    @Autowired
    WebApplicationContext ctx;
    @Autowired
    BarbershopRepository shops;
    @Autowired
    ServiceTypeRepository types;
    @Autowired
    WorkRangeRepository workRanges;
    @Autowired
    ComboBufferRepository buffers;
    @Autowired
    AppUserRepository users;
    @Autowired
    PasswordEncoder encoder;

    MockMvc mvc;
    Barbershop shop;
    Barbershop other;
    Barbershop bare;
    LocalDate day;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
        shop = shops.save(new Barbershop("Flow A", "Alpha", "A küç. 1", 4.0));
        other = shops.save(new Barbershop("Flow B", "Beta", "B küç. 2", 4.0));
        bare = shops.save(new Barbershop("Flow C", "Gamma", "C küç. 3", 4.0));
        ServiceType h = new ServiceType(shop, ServiceKind.HAIRCUT);
        h.setLevels(15, 20, 25);
        types.save(h);
        ServiceType w = new ServiceType(shop, ServiceKind.WASH);
        w.setSingle(10);
        types.save(w);
        ServiceType b = new ServiceType(shop, ServiceKind.BEARD);
        b.setLevels(10, 15, 20);
        types.save(b);
        workRanges.save(new WorkRange(shop, 480, 720));
        workRanges.save(new WorkRange(shop, 840, 1080));
        users.save(new AppUser("fla", encoder.encode("x-Pass-123"), "BARBER", shop));
        users.save(new AppUser("flb", encoder.encode("x-Pass-123"), "BARBER", other));
        day = LocalDate.now().plusDays(1);
    }

    private String body(LocalDate d, String start, String end, String services, String name,
            String phone) {
        return "{\"date\":\"" + d + "\",\"startTime\":\"" + start + "\",\"endTime\":\"" + end
                + "\",\"services\":" + services + ",\"name\":\"" + name
                + "\",\"phone\":\"" + phone + "\"}";
    }

    private MockHttpServletResponse postRaw(Long shopId, String json) throws Exception {
        return mvc.perform(post("/api/shops/" + shopId + "/bookings")
                .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn().getResponse();
    }

    private MockHttpServletResponse book(String start, String end, String services) throws Exception {
        return postRaw(shop.getId(), body(day, start, end, services, NAME, PHONE));
    }

    private int code(String start, String end, String services) throws Exception {
        return book(start, end, services).getStatus();
    }

    private String dayJson() throws Exception {
        return mvc.perform(get("/api/shops/" + shop.getId() + "/day?date=" + day))
                .andReturn().getResponse().getContentAsString();
    }

    private MockHttpServletResponse panelGet(String who, String url) throws Exception {
        return mvc.perform(get(url).with(user(who).roles("BARBER"))).andReturn().getResponse();
    }

    private MockHttpServletResponse panelPut(String who, String url, String json) throws Exception {
        return mvc.perform(put(url).with(user(who).roles("BARBER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn().getResponse();
    }

    private int count(String text, String part) {
        return text.split(Pattern.quote(part), -1).length - 1;
    }

    @Test
    void dayShowsWorkingRanges() throws Exception {
        String d = dayJson();
        assertTrue(d.contains("\"step\":5"), d);
        assertTrue(d.contains("\"earliest\":0"), d);
        assertTrue(d.contains("\"ranges\":[{\"start\":480,\"end\":720},{\"start\":840,\"end\":1080}]"), d);
        assertTrue(d.contains("\"busy\":[]"), d);
    }

    @Test
    void bookingShowsAsBusyWithoutPrivateData() throws Exception {
        var res = book("08:00", "08:20", H_MED);
        assertEquals(200, res.getStatus());
        String b = res.getContentAsString();
        assertTrue(b.contains("\"start\":\"08:00\",\"end\":\"08:20\",\"minutes\":20"), b);
        assertFalse(b.contains(PHONE), b);
        String d = dayJson();
        assertTrue(d.contains("\"busy\":[{\"start\":480,\"end\":500}]"), d);
        assertFalse(d.contains("HAIRCUT"), d);
        assertFalse(d.contains("MEDIUM"), d);
        assertFalse(d.contains(NAME), d);
        assertFalse(d.contains(PHONE), d);
    }

    @Test
    void durationMustEqualTheChosenServices() throws Exception {
        assertEquals(400, code("08:00", "08:15", H_MED));
        assertEquals(400, code("08:00", "08:25", H_MED));
        assertEquals(200, code("08:00", "08:20", H_MED));
        assertEquals(200, code("08:20", "08:35", H_LOW));
        assertEquals(200, code("09:00", "09:25", H_HIGH));
        assertEquals(200, code("10:00", "10:30", HW));
        assertEquals(400, code("11:00", "11:20", HW));
    }

    @Test
    void levelRulesAre400() throws Exception {
        assertEquals(400, code("08:00", "08:20", "[{\"code\":\"HAIRCUT\"}]"));
        assertEquals(400, code("08:00", "08:20", "[{\"code\":\"HAIRCUT\",\"level\":\"HUGE\"}]"));
        assertEquals(400, code("08:00", "08:15", "[{\"code\":\"BEARD\"}]"));
        assertEquals(200, code("08:00", "08:10", W));
    }

    @Test
    void missingDurationsIs409() throws Exception {
        assertEquals(409, postRaw(bare.getId(), body(day, "08:00", "08:20", H_MED, NAME, PHONE)).getStatus());
    }

    @Test
    void outsideWorkingHoursIs409() throws Exception {
        assertEquals(409, code("07:50", "08:10", H_MED));
        assertEquals(409, code("11:50", "12:10", H_MED));
        assertEquals(409, code("12:00", "12:20", H_MED));
        assertEquals(409, code("13:55", "14:15", H_MED));
        assertEquals(409, code("17:50", "18:10", H_MED));
        assertEquals(200, code("11:40", "12:00", H_MED));
    }

    @Test
    void misalignedOrReversedTimesAre400() throws Exception {
        assertEquals(400, code("08:03", "08:23", H_MED));
        assertEquals(400, code("08:20", "08:20", H_MED));
        assertEquals(400, code("08:20", "08:00", H_MED));
    }

    @Test
    void overlapIs409ButAdjacentIsFine() throws Exception {
        assertEquals(200, code("08:00", "08:20", H_MED));
        assertEquals(409, code("08:15", "08:35", H_MED));
        assertEquals(409, code("08:00", "08:20", H_MED));
        assertEquals(200, code("08:20", "08:40", H_MED));
    }

    @Test
    void breakAfterACombinationBlocksNeighboursInBothDirections() throws Exception {
        buffers.save(new ComboBuffer(shop, "WASH", 15));
        assertEquals(200, code("08:00", "08:10", W));
        assertTrue(dayJson().contains("{\"start\":480,\"end\":505}"));
        assertEquals(409, code("08:10", "08:30", H_MED));
        assertEquals(409, code("08:20", "08:40", H_MED));
        assertEquals(200, code("08:25", "08:45", H_MED));
        assertEquals(200, code("09:00", "09:10", W));
        assertEquals(409, code("08:50", "09:00", W));
        assertEquals(200, code("08:45", "09:00", H_LOW));
    }

    @Test
    void invalidRequestsAre400() throws Exception {
        Long id = shop.getId();
        String ok = body(day, "08:00", "08:20", H_MED, NAME, PHONE);
        String[] bad = {
            body(day, "08:00", "08:20", H_MED, "", PHONE),
            body(day, "08:00", "08:20", H_MED, "A".repeat(61), PHONE),
            body(day, "08:00", "08:20", H_MED, NAME, "123"),
            body(day, "08:00", "08:20", "[]", NAME, PHONE),
            body(day, "08:00", "08:20",
                    "[{\"code\":\"HAIRCUT\",\"level\":\"LOW\"},{\"code\":\"HAIRCUT\",\"level\":\"HIGH\"}]", NAME, PHONE),
            body(day, "08:00", "08:20", "[{\"code\":\"COLOR\"}]", NAME, PHONE),
            body(day, "08:00", "08:20",
                    "[{\"code\":\"HAIRCUT\",\"level\":\"LOW\"},{\"code\":\"WASH\"},"
                            + "{\"code\":\"BEARD\",\"level\":\"LOW\"},{\"code\":\"HAIRCUT\",\"level\":\"LOW\"}]",
                    NAME, PHONE),
            body(day, "8:00", "08:20", H_MED, NAME, PHONE),
            body(day, "08:00", "25:00", H_MED, NAME, PHONE),
            body(LocalDate.now().minusDays(1), "08:00", "08:20", H_MED, NAME, PHONE),
            body(LocalDate.now().plusDays(60), "08:00", "08:20", H_MED, NAME, PHONE),
            ok.replace("\"date\":\"" + day + "\",", ""),
            "{bu json deyil"
        };
        for (String json : bad) {
            assertEquals(400, postRaw(id, json).getStatus(), json);
        }
        assertEquals(200, postRaw(id, ok).getStatus());
    }

    @Test
    void unknownShopIs404() throws Exception {
        assertEquals(404, postRaw(999999L, body(day, "08:00", "08:20", H_MED, NAME, PHONE)).getStatus());
        assertEquals(404, mvc.perform(get("/api/shops/999999/day?date=" + day))
                .andReturn().getResponse().getStatus());
        assertEquals(404, mvc.perform(get("/api/shops/999999/services"))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void barberSeesBookingsAndCanCancel() throws Exception {
        assertEquals(200, code("08:00", "08:20", H_MED));
        String own = panelGet("fla", "/api/panel/bookings?date=" + day).getContentAsString();
        assertTrue(own.contains("\"customerName\":\"" + NAME + "\""), own);
        assertTrue(own.contains("\"customerPhone\":\"" + PHONE + "\""), own);
        assertTrue(own.contains("\"start\":\"08:00\",\"end\":\"08:20\""), own);
        assertTrue(own.contains("\"code\":\"HAIRCUT\""), own);
        assertEquals("[]", panelGet("flb", "/api/panel/bookings?date=" + day).getContentAsString());
        Matcher m = Pattern.compile("\"id\":(\\d+)").matcher(own);
        assertTrue(m.find());
        String url = "/api/panel/bookings/" + m.group(1) + "/cancel";
        assertEquals(403, mvc.perform(post(url).with(user("fla").roles("BARBER")))
                .andReturn().getResponse().getStatus());
        assertEquals(404, mvc.perform(post(url).with(user("flb").roles("BARBER")).with(csrf()))
                .andReturn().getResponse().getStatus());
        assertEquals(204, mvc.perform(post(url).with(user("fla").roles("BARBER")).with(csrf()))
                .andReturn().getResponse().getStatus());
        assertTrue(dayJson().contains("\"busy\":[]"));
    }

    @Test
    void hoursChangeCannotStrandABooking() throws Exception {
        assertEquals(200, code("08:00", "08:20", H_MED));
        String bad = "{\"ranges\":[{\"start\":\"09:00\",\"end\":\"12:00\"}]}";
        String good = "{\"ranges\":[{\"start\":\"08:00\",\"end\":\"12:00\"},{\"start\":\"14:00\",\"end\":\"18:00\"}]}";
        assertEquals(409, panelPut("fla", "/api/panel/hours", bad).getStatus());
        assertEquals(200, panelPut("fla", "/api/panel/hours", good).getStatus());
    }

    @Test
    void calendarCountsWorkAndFreeMinutes() throws Exception {
        assertEquals(200, code("08:00", "08:20", H_MED));
        String body = mvc.perform(get("/api/shops/" + shop.getId() + "/calendar?days=3"))
                .andReturn().getResponse().getContentAsString();
        assertEquals(3, count(body, "\"date\""), body);
        assertTrue(body.contains("\"date\":\"" + day + "\",\"workMinutes\":480,\"freeMinutes\":460"), body);
        String bareCal = mvc.perform(get("/api/shops/" + bare.getId() + "/calendar?days=3"))
                .andReturn().getResponse().getContentAsString();
        assertEquals(3, count(bareCal, "\"workMinutes\":0"), bareCal);
        assertEquals(60, count(mvc.perform(get("/api/shops/" + shop.getId() + "/calendar?days=999"))
                .andReturn().getResponse().getContentAsString(), "\"date\""));
        assertEquals(1, count(mvc.perform(get("/api/shops/" + shop.getId() + "/calendar?days=0"))
                .andReturn().getResponse().getContentAsString(), "\"date\""));
    }

    @Test
    void publicServicesShowLevelsAndNoAverage() throws Exception {
        String s = mvc.perform(get("/api/shops/" + shop.getId() + "/services"))
                .andReturn().getResponse().getContentAsString();
        assertTrue(s.contains("\"levels\":true,\"minutes\":null,\"low\":15,\"medium\":20,\"high\":25"), s);
        assertTrue(s.contains("\"levels\":false,\"minutes\":10,\"low\":null,\"medium\":null,\"high\":null"), s);
        assertTrue(s.contains("\"low\":10,\"medium\":15,\"high\":20"), s);
        assertFalse(s.contains("avg"), s);
        String n = mvc.perform(get("/api/shops/" + bare.getId() + "/services"))
                .andReturn().getResponse().getContentAsString();
        assertEquals(3, count(n, "\"low\":null"), n);
    }

    @Test
    void earliestMinuteLogic() {
        LocalDate d = LocalDate.of(2030, 1, 1);
        LocalDateTime now = LocalDateTime.of(2030, 1, 1, 10, 7);
        assertEquals(610, DayPlanner.earliest(d, now));
        assertEquals(0, DayPlanner.earliest(d.plusDays(1), now));
        assertEquals(1440, DayPlanner.earliest(d.minusDays(1), now));
    }

    @Test
    void combosCoverAllSevenInOrder() {
        var all = Combos.all();
        assertEquals(7, all.size());
        assertEquals("HAIRCUT", Combos.key(all.get(0)));
        assertEquals("WASH", Combos.key(all.get(1)));
        assertEquals("BEARD", Combos.key(all.get(2)));
        assertEquals("HAIRCUT,WASH,BEARD", Combos.key(all.get(6)));
        assertEquals(Combos.key(java.util.List.of(ServiceKind.WASH, ServiceKind.HAIRCUT)),
                Combos.key(all.get(3)));
    }
}
