package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

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
class PanelSetupTests {

    private static final String S = "/api/panel/services";
    private static final String H = "/api/panel/hours";
    private static final String B = "/api/panel/buffers";
    private static final String[] COMBOS = {
        "\"HAIRCUT\"", "\"WASH\"", "\"BEARD\"", "\"HAIRCUT\",\"WASH\"",
        "\"HAIRCUT\",\"BEARD\"", "\"WASH\",\"BEARD\"", "\"HAIRCUT\",\"WASH\",\"BEARD\""
    };

    @Autowired
    WebApplicationContext ctx;
    @Autowired
    BarbershopRepository shops;
    @Autowired
    AppUserRepository users;
    @Autowired
    PasswordEncoder encoder;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
        Barbershop a = shops.save(new Barbershop("Setup A", "Alpha", "A küç. 1", 4.0));
        Barbershop b = shops.save(new Barbershop("Setup B", "Beta", "B küç. 2", 4.0));
        users.save(new AppUser("psa", encoder.encode("x-Pass-123"), "BARBER", a));
        users.save(new AppUser("psb", encoder.encode("x-Pass-123"), "BARBER", b));
    }

    private MockHttpServletResponse fetch(String who, String url) throws Exception {
        return mvc.perform(get(url).with(user(who).roles("BARBER"))).andReturn().getResponse();
    }

    private MockHttpServletResponse save(String who, String url, String json) throws Exception {
        return mvc.perform(put(url).with(user(who).roles("BARBER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn().getResponse();
    }

    private int count(String text, String part) {
        return text.split(Pattern.quote(part), -1).length - 1;
    }

    private String svc(int hl, int hm, int hh, int w, int bl, int bm, int bh) {
        return String.format("{\"services\":[{\"code\":\"HAIRCUT\",\"low\":%d,\"medium\":%d,\"high\":%d},"
                + "{\"code\":\"WASH\",\"minutes\":%d},"
                + "{\"code\":\"BEARD\",\"low\":%d,\"medium\":%d,\"high\":%d}]}",
                hl, hm, hh, w, bl, bm, bh);
    }

    private String hours(String... ranges) {
        StringBuilder sb = new StringBuilder("{\"ranges\":[");
        for (int i = 0; i < ranges.length; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"start\":\"").append(ranges[i], 0, 5).append("\",\"end\":\"")
                    .append(ranges[i].substring(6)).append("\"}");
        }
        return sb.append("]}").toString();
    }

    private String buffers(String[] combos, int... mins) {
        StringBuilder sb = new StringBuilder("{\"buffers\":[");
        for (int i = 0; i < combos.length; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"services\":[").append(combos[i]).append("],\"afterMinutes\":")
                    .append(mins[i]).append('}');
        }
        return sb.append("]}").toString();
    }

    @Test
    void servicesWithoutLoginIs401() throws Exception {
        assertEquals(401, mvc.perform(get(S)).andReturn().getResponse().getStatus());
    }

    @Test
    void servicesSaveLevelsAndBarbersAreIsolated() throws Exception {
        String first = fetch("psa", S).getContentAsString();
        assertEquals(3, count(first, "\"minutes\":null"), first);
        assertEquals(3, count(first, "\"low\":null"), first);
        assertEquals(200, save("psa", S, svc(15, 20, 25, 10, 10, 15, 20)).getStatus());
        String again = fetch("psa", S).getContentAsString();
        assertTrue(again.contains("\"levels\":true,\"minutes\":null,\"low\":15,\"medium\":20,\"high\":25"), again);
        assertTrue(again.contains("\"levels\":false,\"minutes\":10,\"low\":null"), again);
        assertTrue(again.contains("\"low\":10,\"medium\":15,\"high\":20"), again);
        assertEquals(1, count(again, "\"code\":\"HAIRCUT\""), again);
        assertEquals(3, count(fetch("psb", S).getContentAsString(), "\"low\":null"));
    }

    @Test
    void invalidServiceDurationsAre400() throws Exception {
        String[] bad = {
            svc(20, 15, 25, 10, 10, 15, 20),
            svc(15, 25, 20, 10, 10, 15, 20),
            svc(3, 20, 25, 10, 10, 15, 20),
            svc(15, 20, 245, 10, 10, 15, 20),
            svc(15, 22, 25, 10, 10, 15, 20),
            svc(15, 20, 25, 12, 10, 15, 20),
            "{\"services\":[{\"code\":\"HAIRCUT\",\"low\":15,\"medium\":20},"
                    + "{\"code\":\"WASH\",\"minutes\":10},"
                    + "{\"code\":\"BEARD\",\"low\":10,\"medium\":15,\"high\":20}]}",
            "{\"services\":[{\"code\":\"HAIRCUT\",\"low\":15,\"medium\":20,\"high\":25},"
                    + "{\"code\":\"WASH\"},"
                    + "{\"code\":\"BEARD\",\"low\":10,\"medium\":15,\"high\":20}]}",
            "{\"services\":[{\"code\":\"HAIRCUT\",\"low\":15,\"medium\":20,\"high\":25},"
                    + "{\"code\":\"HAIRCUT\",\"low\":15,\"medium\":20,\"high\":25},"
                    + "{\"code\":\"WASH\",\"minutes\":10}]}",
            "{\"services\":[{\"code\":\"HAIRCUT\",\"low\":15,\"medium\":20,\"high\":25},"
                    + "{\"code\":\"WASH\",\"minutes\":10},"
                    + "{\"code\":\"COLOR\",\"low\":10,\"medium\":15,\"high\":20}]}",
            "{\"services\":[{\"code\":\"HAIRCUT\",\"low\":15,\"medium\":20,\"high\":25},"
                    + "{\"code\":\"WASH\",\"minutes\":10}]}",
            "{bu json deyil"
        };
        for (String json : bad) {
            assertEquals(400, save("psa", S, json).getStatus(), json);
        }
    }

    @Test
    void saveWithoutCsrfTokenIs403() throws Exception {
        int status = mvc.perform(put(H).with(user("psa").roles("BARBER"))
                .contentType(MediaType.APPLICATION_JSON).content(hours("08:00-12:00")))
                .andReturn().getResponse().getStatus();
        assertEquals(403, status);
    }

    @Test
    void hoursAreSortedMergedAndPersist() throws Exception {
        assertEquals("[]", fetch("psa", H).getContentAsString());
        var merged = save("psa", H, hours("11:00-14:00", "08:00-12:00"));
        assertEquals(200, merged.getStatus());
        assertEquals("[{\"start\":\"08:00\",\"end\":\"14:00\"}]", merged.getContentAsString());
        var split = save("psa", H, hours("14:00-18:00", "08:00-12:00"));
        assertEquals("[{\"start\":\"08:00\",\"end\":\"12:00\"},{\"start\":\"14:00\",\"end\":\"18:00\"}]",
                split.getContentAsString());
        assertEquals(split.getContentAsString(), fetch("psa", H).getContentAsString());
        assertEquals("[]", fetch("psb", H).getContentAsString());
    }

    @Test
    void invalidHoursAre400() throws Exception {
        String[] bad = {
            hours("12:00-08:00"),
            hours("08:00-08:00"),
            hours("08:03-12:00"),
            hours("04:00-08:00"),
            hours("08:00-23:59"),
            "{\"ranges\":[]}",
            hours("05:00-06:00", "06:00-07:00", "07:00-08:00", "08:00-09:00",
                    "09:00-10:00", "10:00-11:00", "11:00-12:00"),
            "{\"ranges\":[{\"start\":\"8:00\",\"end\":\"12:00\"}]}",
            "{\"ranges\":null}",
            "{bu json deyil"
        };
        for (String json : bad) {
            assertEquals(400, save("psa", H, json).getStatus(), json);
        }
    }

    @Test
    void buffersDefaultToZeroAndSave() throws Exception {
        String first = fetch("psa", B).getContentAsString();
        assertEquals(7, count(first, "\"afterMinutes\":0"), first);
        assertEquals(200, save("psa", B, buffers(COMBOS, 0, 15, 0, 0, 0, 0, 30)).getStatus());
        String again = fetch("psa", B).getContentAsString();
        assertEquals(5, count(again, "\"afterMinutes\":0"), again);
        assertEquals(1, count(again, "\"afterMinutes\":15"), again);
        assertEquals(1, count(again, "\"afterMinutes\":30"), again);
        assertEquals(7, count(fetch("psb", B).getContentAsString(), "\"afterMinutes\":0"));
    }

    @Test
    void invalidBuffersAre400() throws Exception {
        String[] dup = COMBOS.clone();
        dup[6] = COMBOS[0];
        String[] unknown = COMBOS.clone();
        unknown[2] = "\"COLOR\"";
        String[] empty = COMBOS.clone();
        empty[2] = "";
        String[] six = java.util.Arrays.copyOf(COMBOS, 6);
        String[] bad = {
            buffers(six, 0, 0, 0, 0, 0, 0),
            buffers(dup, 0, 0, 0, 0, 0, 0, 0),
            buffers(COMBOS, 0, 7, 0, 0, 0, 0, 0),
            buffers(COMBOS, 0, 0, 0, 0, 0, 0, 241),
            buffers(unknown, 0, 0, 0, 0, 0, 0, 0),
            buffers(empty, 0, 0, 0, 0, 0, 0, 0),
            "{bu json deyil"
        };
        for (String json : bad) {
            assertEquals(400, save("psa", B, json).getStatus(), json);
        }
    }
}
