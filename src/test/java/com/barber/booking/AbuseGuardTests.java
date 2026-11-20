package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "app.rate-limit.enabled=true")
@Transactional
class AbuseGuardTests {

    private static final AtomicInteger NEXT = new AtomicInteger(1);

    @Autowired
    WebApplicationContext ctx;
    @Autowired
    BarbershopRepository shops;
    @Autowired
    ServiceTypeRepository types;
    @Autowired
    WorkRangeRepository workRanges;

    MockMvc mvc;
    LocalDate day;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
        day = LocalDate.now().plusDays(1);
    }

    private Barbershop shop(String name) {
        Barbershop s = shops.save(new Barbershop(name, "Master", "Addr 1", 4.0));
        ServiceType w = new ServiceType(s, ServiceKind.WASH);
        w.setSingle(10);
        types.save(w);
        workRanges.save(new WorkRange(s, 480, 720));
        return s;
    }

    private int book(Barbershop s, String start, String end, String phone) throws Exception {
        String json = "{\"date\":\"" + day + "\",\"startTime\":\"" + start + "\",\"endTime\":\"" + end
                + "\",\"services\":[{\"code\":\"WASH\"}],\"name\":\"Elvin\",\"phone\":\"" + phone + "\"}";
        int n = NEXT.getAndIncrement();
        String ip = "10." + (n / 250) + "." + (n % 250) + ".1";
        return mvc.perform(post("/api/shops/" + s.getId() + "/bookings")
                .contentType(MediaType.APPLICATION_JSON).content(json)
                .with(r -> { r.setRemoteAddr(ip); return r; }))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void fourthUpcomingBookingOfOnePhoneAtOneShopIs429() throws Exception {
        Barbershop a = shop("Abuse A");
        String p = "+994501110001";
        assertEquals(200, book(a, "08:00", "08:10", p));
        assertEquals(200, book(a, "08:10", "08:20", p));
        assertEquals(200, book(a, "08:20", "08:30", p));
        assertEquals(429, book(a, "08:30", "08:40", p));
        assertEquals(200, book(a, "08:30", "08:40", "+994501110009"));
    }

    @Test
    void plusPrefixVariantsCountAsTheSamePhone() throws Exception {
        Barbershop a = shop("Abuse B");
        assertEquals(200, book(a, "08:00", "08:10", "+994501110002"));
        assertEquals(200, book(a, "08:10", "08:20", "994501110002"));
        assertEquals(200, book(a, "08:20", "08:30", "+994501110002"));
        assertEquals(429, book(a, "08:30", "08:40", "994501110002"));
    }

    @Test
    void dailyLimitCountsAcrossShops() throws Exception {
        Barbershop a = shop("Abuse C1");
        Barbershop b = shop("Abuse C2");
        Barbershop c = shop("Abuse C3");
        String p = "+994501110003";
        for (String t : new String[] {"08:00", "08:10", "08:20"}) {
            String end = endOf(t);
            assertEquals(200, book(a, t, end, p));
            assertEquals(200, book(b, t, end, p));
        }
        assertEquals(429, book(c, "08:00", "08:10", p));
        assertEquals(200, book(c, "08:00", "08:10", "+994501110099"));
    }

    @Test
    void conflictsDoNotCountTowardsTheLimit() throws Exception {
        Barbershop a = shop("Abuse D");
        String p = "+994501110004";
        assertEquals(200, book(a, "08:00", "08:10", p));
        for (int i = 0; i < 5; i++) assertEquals(409, book(a, "08:00", "08:10", p));
        assertEquals(200, book(a, "08:10", "08:20", p));
        assertEquals(200, book(a, "08:20", "08:30", p));
        assertEquals(429, book(a, "08:30", "08:40", p));
    }

    private static String endOf(String start) {
        int m = Integer.parseInt(start.substring(0, 2)) * 60 + Integer.parseInt(start.substring(3)) + 10;
        return String.format("%02d:%02d", m / 60, m % 60);
    }
}
