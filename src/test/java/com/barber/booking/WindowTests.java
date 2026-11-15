package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.time.LocalDate;
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
class WindowTests {

    private static final String BASE = "/api/panel/windows?date=";
    private static final String ALL = "\"HAIRCUT\",\"WASH\",\"BEARD\"";

    @Autowired
    WebApplicationContext ctx;
    @Autowired
    AppUserRepository users;
    @Autowired
    BarbershopRepository shops;
    @Autowired
    ServiceTypeRepository types;
    @Autowired
    PasswordEncoder encoder;

    MockMvc mvc;
    Barbershop shopA;
    LocalDate day;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
        shopA = shops.save(new Barbershop("Win A", "Alpha", "A küç. 1", 4.0));
        Barbershop b = shops.save(new Barbershop("Win B", "Beta", "B küç. 2", 4.0));
        Barbershop c = shops.save(new Barbershop("Win C", "Gamma", "C küç. 3", 4.0));
        users.save(new AppUser("wina", encoder.encode("x-Pass-123"), "BARBER", shopA));
        users.save(new AppUser("winb", encoder.encode("x-Pass-123"), "BARBER", b));
        users.save(new AppUser("winc", encoder.encode("x-Pass-123"), "BARBER", c));
        durations(shopA);
        durations(b);
        day = LocalDate.now().plusDays(1);
    }

    private void durations(Barbershop shop) {
        for (ServiceKind k : ServiceKind.values()) {
            ServiceType t = types.save(new ServiceType(shop, k));
            t.setDurationMinutes(k == ServiceKind.HAIRCUT ? 30 : k == ServiceKind.WASH ? 15 : 20);
        }
    }

    private String w(String start, String... kinds) {
        StringBuilder c = new StringBuilder();
        for (String k : kinds) {
            if (c.length() > 0) c.append(',');
            c.append('"').append(k).append('"');
        }
        return "{\"startTime\":\"" + start + "\",\"cubes\":[" + c + "]}";
    }

    private String req(String... ws) {
        return "{\"windows\":[" + String.join(",", ws) + "]}";
    }

    private MockHttpServletResponse fetch(String who, LocalDate d) throws Exception {
        return mvc.perform(get(BASE + d).with(user(who).roles("BARBER"))).andReturn().getResponse();
    }

    private MockHttpServletResponse save(String who, LocalDate d, String json) throws Exception {
        return mvc.perform(put(BASE + d).with(user(who).roles("BARBER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn().getResponse();
    }

    private int count(String text, String part) {
        return text.split(Pattern.quote(part), -1).length - 1;
    }

    @Test
    void windowsWithoutLoginIs401() throws Exception {
        assertEquals(401, mvc.perform(get(BASE + day)).andReturn().getResponse().getStatus());
    }

    @Test
    void emptyDayIsEmptyListAndDateIsLimited() throws Exception {
        assertEquals("[]", fetch("wina", day).getContentAsString());
        assertEquals(400, fetch("wina", LocalDate.now().plusDays(60)).getStatus());
        assertEquals(400, fetch("wina", LocalDate.now().minusDays(1)).getStatus());
        assertEquals(400, mvc.perform(get("/api/panel/windows").with(user("wina").roles("BARBER")))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void saveWithoutCsrfTokenIs403() throws Exception {
        int status = mvc.perform(put(BASE + day).with(user("wina").roles("BARBER"))
                .contentType(MediaType.APPLICATION_JSON).content(req(w("10:00", "HAIRCUT"))))
                .andReturn().getResponse().getStatus();
        assertEquals(403, status);
    }

    @Test
    void saveWithoutServiceDurationsIs409() throws Exception {
        assertEquals(409, save("winc", day, req(w("10:00", "HAIRCUT"))).getStatus());
    }

    @Test
    void threeCubeWindowEndsAfterTheSum() throws Exception {
        var res = save("wina", day, req(w("10:00", "HAIRCUT", "WASH", "BEARD")));
        assertEquals(200, res.getStatus());
        String body = res.getContentAsString();
        assertTrue(body.contains("\"endTime\":\"11:05\""), body);
        assertTrue(body.contains("\"totalMinutes\":65"), body);
        assertEquals(3, count(body, "\"minutes\""));
    }

    @Test
    void fewerCubesAreAllowed() throws Exception {
        String one = save("wina", day, req(w("10:00", "HAIRCUT"))).getContentAsString();
        assertTrue(one.contains("\"endTime\":\"10:30\""), one);
        String two = save("wina", day, req(w("10:00", "WASH", "BEARD"))).getContentAsString();
        assertTrue(two.contains("\"endTime\":\"10:35\""), two);
    }

    @Test
    void chainedWindowsAreAcceptedAndReturnedInOrder() throws Exception {
        var res = save("wina", day, req(w("11:05", "HAIRCUT"), w("10:00", "HAIRCUT", "WASH", "BEARD")));
        assertEquals(200, res.getStatus());
        String body = res.getContentAsString();
        assertEquals(2, count(body, "\"startTime\""));
        assertTrue(body.contains("\"endTime\":\"11:35\""), body);
        assertTrue(body.indexOf("\"startTime\":\"10:00\"") < body.indexOf("\"startTime\":\"11:05\""));
    }

    @Test
    void overlappingWindowsAre400() throws Exception {
        assertEquals(400, save("wina", day,
                req(w("10:00", "HAIRCUT", "WASH", "BEARD"), w("11:00", "HAIRCUT"))).getStatus());
    }

    @Test
    void invalidRequestsAre400() throws Exception {
        String[] bad = {
            req(w("10:00")),
            req(w("10:00", "HAIRCUT", "WASH", "BEARD", "HAIRCUT")),
            req(w("10:00", "HAIRCUT", "HAIRCUT")),
            req(w("10:00", "COLOR")),
            req(w("10:07", "HAIRCUT")),
            req(w("07:55", "HAIRCUT")),
            req(w("21:00", "HAIRCUT", "WASH", "BEARD")),
            req(w("21:45", "HAIRCUT")),
            req(w("25:00", "HAIRCUT")),
            "{}",
            "{\"windows\":null}",
            "{bu json deyil"
        };
        for (String json : bad) {
            assertEquals(400, save("wina", day, json).getStatus(), json);
        }
        assertEquals(400, save("wina", LocalDate.now().minusDays(1), req(w("10:00", "HAIRCUT"))).getStatus());
        assertEquals(400, save("wina", LocalDate.now().plusDays(60), req(w("10:00", "HAIRCUT"))).getStatus());
    }

    @Test
    void saveReplacesTheWholeDay() throws Exception {
        save("wina", day, req(w("10:00", "HAIRCUT", "WASH", "BEARD")));
        save("wina", day, req(w("12:00", "HAIRCUT")));
        String body = fetch("wina", day).getContentAsString();
        assertTrue(body.contains("\"startTime\":\"12:00\""), body);
        assertFalse(body.contains("\"startTime\":\"10:00\""), body);
        assertEquals(200, save("wina", day, req()).getStatus());
        assertEquals("[]", fetch("wina", day).getContentAsString());
    }

    @Test
    void barbersDoNotSeeEachOthersWindows() throws Exception {
        save("wina", day, req(w("10:00", "HAIRCUT")));
        assertEquals("[]", fetch("winb", day).getContentAsString());
        assertEquals(200, save("winb", day, req(w("09:00", "HAIRCUT"))).getStatus());
        String a = fetch("wina", day).getContentAsString();
        assertTrue(a.contains("\"startTime\":\"10:00\""), a);
        assertFalse(a.contains("\"startTime\":\"09:00\""), a);
    }

    @Test
    void changingServiceDurationsDoesNotShiftExistingWindows() throws Exception {
        save("wina", day, req(w("10:00", "HAIRCUT", "WASH", "BEARD")));
        types.findByBarbershopIdOrderById(shopA.getId()).forEach(t -> t.setDurationMinutes(100));
        String body = fetch("wina", day).getContentAsString();
        assertTrue(body.contains("\"totalMinutes\":65"), body);
        assertTrue(body.contains("\"endTime\":\"11:05\""), body);
    }
}
