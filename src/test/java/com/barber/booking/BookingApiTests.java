package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class BookingApiTests {

    private static final String OK_BODY = "{\"name\":\"Elvin\",\"phone\":\"+994501234567\"}";

    @Autowired
    WebApplicationContext ctx;
    @Autowired
    SlotRepository slots;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(ctx).build();
    }

    private String getBody(String url, int expectedStatus) throws Exception {
        return mvc.perform(get(url)).andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString();
    }

    private int postStatus(String url, String body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getStatus();
    }

    private int count(String text, String part) {
        return text.split(Pattern.quote(part), -1).length - 1;
    }

    private Slot lastFreeSlot() {
        List<Slot> free = slots.findAll().stream()
                .filter(s -> !s.isBooked()
                        && s.getStartTime().isAfter(LocalDateTime.now().plusHours(1)))
                .toList();
        return free.get(free.size() - 1);
    }

    private String tomorrow() {
        return LocalDate.now().plusDays(1).toString();
    }

    @Test
    void shopsReturnMasterAndRating() throws Exception {
        String body = getBody("/api/shops", 200);
        assertEquals(8, count(body, "\"masterName\""));
        assertTrue(body.contains("\"rating\""));
    }

    @Test
    void dayReturnsAllTwelveSlotsWithStatus() throws Exception {
        String body = getBody("/api/shops/1/day?date=" + tomorrow(), 200);
        assertEquals(12, count(body, "\"status\""));
        assertTrue(body.contains("\"FREE\"") || body.contains("\"BOOKED\""));
    }

    @Test
    void dayDoesNotLeakCustomerData() throws Exception {
        String body = getBody("/api/shops/1/day?date=" + tomorrow(), 200);
        assertFalse(body.contains("+994500000000"));
        assertFalse(body.toLowerCase().contains("customer"));
    }

    @Test
    void dayForUnknownShopIs404() throws Exception {
        getBody("/api/shops/999/day?date=" + tomorrow(), 404);
    }

    @Test
    void dayWithoutDateIs400() throws Exception {
        getBody("/api/shops/1/day", 400);
    }

    @Test
    void availabilityIsCappedAt62Days() throws Exception {
        String body = getBody("/api/shops/1/availability?days=999999", 200);
        assertEquals(62, count(body, "\"date\""));
    }

    @Test
    void bookWithBlankNameIs400() throws Exception {
        assertEquals(400, postStatus("/api/slots/1/book",
                "{\"name\":\"\",\"phone\":\"+994501234567\"}"));
    }

    @Test
    void bookWithBadPhoneIs400() throws Exception {
        assertEquals(400, postStatus("/api/slots/1/book",
                "{\"name\":\"Elvin\",\"phone\":\"123\"}"));
    }

    @Test
    void bookWithBrokenJsonIs400() throws Exception {
        assertEquals(400, postStatus("/api/slots/1/book", "{bu json deyil"));
    }

    @Test
    void bookWithNonNumericIdIs400() throws Exception {
        assertEquals(400, postStatus("/api/slots/abc/book", OK_BODY));
    }

    @Test
    void getOnBookEndpointIs405() throws Exception {
        getBody("/api/slots/1/book", 405);
    }

    @Test
    void bookUnknownSlotIs404() throws Exception {
        assertEquals(404, postStatus("/api/slots/999999/book", OK_BODY));
    }

    @Test
    void bookingSameSlotTwiceGives200Then409() throws Exception {
        String url = "/api/slots/" + lastFreeSlot().getId() + "/book";
        assertEquals(200, postStatus(url, OK_BODY));
        assertEquals(409, postStatus(url, OK_BODY));
    }

    @Test
    void concurrentBookingOnlyOneWins() throws Exception {
        String url = "/api/slots/" + lastFreeSlot().getId() + "/book";
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return postStatus(url, OK_BODY);
            }));
        }
        start.countDown();
        List<Integer> codes = new ArrayList<>();
        for (Future<Integer> f : results) codes.add(f.get(20, TimeUnit.SECONDS));
        pool.shutdown();
        assertEquals(1, codes.stream().filter(c -> c == 200).count(), "codes=" + codes);
        assertEquals(1, codes.stream().filter(c -> c == 409).count(), "codes=" + codes);
    }
}
