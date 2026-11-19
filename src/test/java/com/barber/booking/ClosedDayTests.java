package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDate;
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
class ClosedDayTests {

    @Autowired
    WebApplicationContext ctx;
    @Autowired
    BarbershopRepository shops;
    @Autowired
    ServiceTypeRepository types;
    @Autowired
    WorkRangeRepository workRanges;
    @Autowired
    AppUserRepository users;
    @Autowired
    PasswordEncoder encoder;

    MockMvc mvc;
    Barbershop shop;
    Barbershop other;
    LocalDate day;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
        shop = shops.save(new Barbershop("Closed A", "Alpha", "A kuc. 1", 4.0));
        other = shops.save(new Barbershop("Closed B", "Beta", "B kuc. 2", 4.0));
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
        workRanges.save(new WorkRange(other, 480, 720));
        users.save(new AppUser("cla", encoder.encode("x-Pass-123"), "BARBER", shop));
        users.save(new AppUser("clb", encoder.encode("x-Pass-123"), "BARBER", other));
        day = LocalDate.now().plusDays(1);
    }

    private MockHttpServletResponse panelGet(String who, String url) throws Exception {
        return mvc.perform(get(url).with(user(who).roles("BARBER"))).andReturn().getResponse();
    }

    private MockHttpServletResponse panelPost(String who, String url) throws Exception {
        return mvc.perform(post(url).with(user(who).roles("BARBER")).with(csrf()))
                .andReturn().getResponse();
    }

    private MockHttpServletResponse panelDelete(String who, String url) throws Exception {
        return mvc.perform(delete(url).with(user(who).roles("BARBER")).with(csrf()))
                .andReturn().getResponse();
    }

    private String publicGet(String url) throws Exception {
        return mvc.perform(get(url)).andReturn().getResponse().getContentAsString();
    }

    private int bookPublic(Long shopId) throws Exception {
        String json = "{\"date\":\"" + day + "\",\"startTime\":\"08:00\",\"endTime\":\"08:20\","
                + "\"services\":[{\"code\":\"HAIRCUT\",\"level\":\"MEDIUM\"}],"
                + "\"name\":\"Elvin\",\"phone\":\"+994501234567\"}";
        return mvc.perform(post("/api/shops/" + shopId + "/bookings")
                .contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn().getResponse().getStatus();
    }

    private long firstId(String json) {
        Matcher m = Pattern.compile("\"id\":(\\d+)").matcher(json);
        assertTrue(m.find(), json);
        return Long.parseLong(m.group(1));
    }

    @Test
    void closedEndpointsNeedLoginAndCsrfToken() throws Exception {
        assertEquals(401, mvc.perform(get("/api/panel/closed")).andReturn().getResponse().getStatus());
        assertEquals(403, mvc.perform(post("/api/panel/closed/" + day)
                .with(user("cla").roles("BARBER"))).andReturn().getResponse().getStatus());
    }

    @Test
    void closingAFreeDayHidesItFromCustomersAndReopeningRestoresIt() throws Exception {
        assertEquals(204, panelPost("cla", "/api/panel/closed/" + day).getStatus());
        assertTrue(panelGet("cla", "/api/panel/closed").getContentAsString().contains(day.toString()));
        String cal = publicGet("/api/shops/" + shop.getId() + "/calendar?days=3");
        assertTrue(cal.contains("\"date\":\"" + day + "\",\"workMinutes\":0,\"freeMinutes\":0"), cal);
        assertTrue(publicGet("/api/shops/" + shop.getId() + "/day?date=" + day).contains("\"ranges\":[]"));
        assertEquals(409, bookPublic(shop.getId()));

        assertEquals(204, panelDelete("cla", "/api/panel/closed/" + day).getStatus());
        assertEquals("[]", panelGet("cla", "/api/panel/closed").getContentAsString());
        assertTrue(publicGet("/api/shops/" + shop.getId() + "/day?date=" + day)
                .contains("\"ranges\":[{\"start\":480"));
        assertEquals(200, bookPublic(shop.getId()));
    }

    @Test
    void closingADayWithBookingsNeedsThemListedAndCancelsOnlyThem() throws Exception {
        assertEquals(200, bookPublic(shop.getId()));
        long id = firstId(panelGet("cla", "/api/panel/bookings?date=" + day).getContentAsString());
        String url = "/api/panel/closed/" + day;
        assertEquals(409, panelPost("cla", url).getStatus());
        assertEquals(409, panelPost("cla", url + "?cancelBookingIds=999999").getStatus());
        assertTrue(panelGet("cla", "/api/panel/bookings?date=" + day).getContentAsString().contains("Elvin"));
        assertEquals("[]", panelGet("cla", "/api/panel/closed").getContentAsString());

        assertEquals(204, panelPost("cla", url + "?cancelBookingIds=" + id).getStatus());
        assertEquals("[]", panelGet("cla", "/api/panel/bookings?date=" + day).getContentAsString());
        assertTrue(panelGet("cla", "/api/panel/closed").getContentAsString().contains(day.toString()));
    }

    @Test
    void invalidDatesAre400() throws Exception {
        assertEquals(400, panelPost("cla", "/api/panel/closed/" + LocalDate.now().minusDays(1)).getStatus());
        assertEquals(400, panelPost("cla", "/api/panel/closed/" + LocalDate.now().plusDays(60)).getStatus());
        assertEquals(400, panelPost("cla", "/api/panel/closed/abc").getStatus());
        assertEquals(400, panelDelete("cla", "/api/panel/closed/" + LocalDate.now().plusDays(60)).getStatus());
    }

    @Test
    void closeAndReopenAreIdempotent() throws Exception {
        assertEquals(204, panelPost("cla", "/api/panel/closed/" + day).getStatus());
        assertEquals(204, panelPost("cla", "/api/panel/closed/" + day).getStatus());
        assertEquals(1, panelGet("cla", "/api/panel/closed").getContentAsString().split(day.toString(), -1).length - 1);
        assertEquals(204, panelDelete("cla", "/api/panel/closed/" + day).getStatus());
        assertEquals(204, panelDelete("cla", "/api/panel/closed/" + day).getStatus());
        assertEquals("[]", panelGet("cla", "/api/panel/closed").getContentAsString());
    }

    @Test
    void closedDaysAreIsolatedBetweenBarbers() throws Exception {
        assertEquals(204, panelPost("cla", "/api/panel/closed/" + day).getStatus());
        assertEquals("[]", panelGet("clb", "/api/panel/closed").getContentAsString());
        assertTrue(publicGet("/api/shops/" + other.getId() + "/day?date=" + day)
                .contains("\"ranges\":[{\"start\":480"));
    }
}
