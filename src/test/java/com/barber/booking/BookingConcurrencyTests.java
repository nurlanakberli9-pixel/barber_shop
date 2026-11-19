package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDate;
import java.util.ArrayList;
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
    ServiceTypeRepository types;
    @Autowired
    WorkRangeRepository workRanges;
    @Autowired
    BookingRepository bookings;
    @Autowired
    PlatformTransactionManager txManager;

    MockMvc mvc;
    TransactionTemplate tx;
    Long shopId;
    LocalDate day;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        mvc = MockMvcBuilders.webAppContextSetup(ctx).apply(springSecurity()).build();
        day = LocalDate.now().plusDays(1);
        shopId = tx.execute(s -> {
            Barbershop shop = shops.save(new Barbershop("Race Salon", "Racer", "R küç. 1", 4.0));
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
            return shop.getId();
        });
    }

    @AfterEach
    void cleanUp() {
        if (shopId == null) return;
        tx.executeWithoutResult(s -> {
            bookings.deleteAll(bookings.findByBarbershopIdAndBookingDateOrderByStartMinute(shopId, day));
            workRanges.deleteAll(workRanges.findByBarbershopIdOrderByStartMinute(shopId));
            types.deleteAll(types.findByBarbershopIdOrderById(shopId));
            shops.deleteById(shopId);
        });
    }

    private List<Integer> race(String[][] slots) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(slots.length);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (String[] t : slots) {
            String json = "{\"date\":\"" + day + "\",\"startTime\":\"" + t[0] + "\",\"endTime\":\""
                    + t[1] + "\",\"services\":[{\"code\":\"HAIRCUT\",\"level\":\"MEDIUM\"}],"
                    + "\"name\":\"Elvin\",\"phone\":\"+994501234567\"}";
            futures.add(pool.submit(() -> {
                go.await();
                return mvc.perform(post("/api/shops/" + shopId + "/bookings")
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

    private int stored() {
        return bookings.findByBarbershopIdAndBookingDateOrderByStartMinute(shopId, day).size();
    }

    @Test
    void sameTimeOnlyOneWins() throws Exception {
        List<Integer> codes = race(new String[][] {{"08:00", "08:20"}, {"08:00", "08:20"}});
        assertEquals(1, codes.stream().filter(c -> c == 200).count(), "codes=" + codes);
        assertEquals(1, codes.stream().filter(c -> c == 409).count(), "codes=" + codes);
        assertEquals(1, stored());
    }

    @Test
    void overlappingTimesOnlyOneWins() throws Exception {
        List<Integer> codes = race(new String[][] {{"08:00", "08:20"}, {"08:10", "08:30"}});
        assertEquals(1, codes.stream().filter(c -> c == 200).count(), "codes=" + codes);
        assertEquals(1, codes.stream().filter(c -> c == 409).count(), "codes=" + codes);
        assertEquals(1, stored());
    }

    @Test
    void separateTimesAllSucceed() throws Exception {
        List<Integer> codes = race(new String[][] {{"08:00", "08:20"}, {"08:20", "08:40"}, {"08:40", "09:00"}});
        assertEquals(List.of(200, 200, 200), codes);
        assertEquals(3, stored());
    }
}
