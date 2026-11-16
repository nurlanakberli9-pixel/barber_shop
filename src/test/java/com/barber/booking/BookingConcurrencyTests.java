package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class BookingConcurrencyTests {

    @Autowired
    WebApplicationContext ctx;
    @Autowired
    BarbershopRepository shops;
    @Autowired
    WorkWindowRepository windows;
    @Autowired
    BookingRepository bookings;
    @Autowired
    PlatformTransactionManager txManager;

    MockMvc mvc;
    TransactionTemplate tx;
    Long shopId;
    LocalDate day;

    private static int minutesOf(ServiceKind k) {
        return k == ServiceKind.HAIRCUT ? 30 : k == ServiceKind.WASH ? 15 : 20;
    }

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
        day = LocalDate.now().plusDays(1);
        shopId = shops.save(new Barbershop("Race Salon", "Racer", "R küç. 1", 4.0)).getId();
    }

    @AfterEach
    void cleanUp() {
        if (shopId == null) return;
        tx.executeWithoutResult(s -> {
            bookings.deleteAll(
                    bookings.findByBarbershopIdAndBookingDateOrderByStartMinute(shopId, day));
            windows.deleteAll(
                    windows.findByBarbershopIdAndWindowDateOrderByStartTime(shopId, day));
            shops.deleteById(shopId);
        });
    }

    private Long newWindow(String start, ServiceKind... kinds) {
        return tx.execute(s -> {
            Barbershop shop = shops.findById(shopId).orElseThrow();
            WorkWindow w = new WorkWindow(shop, day, LocalTime.parse(start));
            for (ServiceKind k : kinds) w.addCube(k, minutesOf(k));
            return windows.save(w).getId();
        });
    }

    private List<Integer> race(Long windowId, String... services) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(services.length);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (String svc : services) {
            String json = "{\"services\":" + svc + ",\"name\":\"Elvin\",\"phone\":\"+994501234567\"}";
            futures.add(pool.submit(() -> {
                go.await();
                return mvc.perform(post("/api/shops/" + shopId + "/windows/" + windowId + "/bookings")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                        .andReturn().getResponse().getStatus();
            }));
        }
        go.countDown();
        List<Integer> codes = new ArrayList<>();
        for (Future<Integer> f : futures) codes.add(f.get(30, TimeUnit.SECONDS));
        pool.shutdown();
        return codes;
    }

    @Test
    void sameCubeOnlyOneWins() throws Exception {
        Long wid = newWindow("10:00", ServiceKind.HAIRCUT);
        List<Integer> codes = race(wid, "[\"HAIRCUT\"]", "[\"HAIRCUT\"]");
        assertEquals(1, codes.stream().filter(c -> c == 200).count(), "codes=" + codes);
        assertEquals(1, codes.stream().filter(c -> c == 409).count(), "codes=" + codes);
        assertEquals(1, bookings.findByBarbershopIdAndBookingDateOrderByStartMinute(shopId, day).size());
    }

    @Test
    void differentCubesAllFitWithoutOverlap() throws Exception {
        Long wid = newWindow("10:00", ServiceKind.HAIRCUT, ServiceKind.WASH, ServiceKind.BEARD);
        List<Integer> codes = race(wid, "[\"HAIRCUT\"]", "[\"WASH\"]", "[\"BEARD\"]");
        assertEquals(List.of(200, 200, 200), codes);
        List<Booking> list = new ArrayList<>(
                bookings.findByBarbershopIdAndBookingDateOrderByStartMinute(shopId, day));
        list.sort(Comparator.comparingInt(Booking::getStartMinute));
        assertEquals(3, list.size());
        assertEquals(600, list.get(0).getStartMinute());
        for (int i = 1; i < list.size(); i++) {
            assertTrue(list.get(i - 1).getEndMinute() <= list.get(i).getStartMinute(), "üst-üstə düşür");
        }
        assertEquals(665, list.get(2).getEndMinute());
    }
}
