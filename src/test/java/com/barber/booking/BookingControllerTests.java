package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class BookingControllerTests {

    @Autowired
    BookingController controller;
    @Autowired
    SlotRepository slots;

    private static final BookingController.BookRequest REQ =
            new BookingController.BookRequest("Elvin", "+994501234567");

    private Slot freeSlot(int skip) {
        return slots.findAll().stream()
                .filter(s -> !s.isBooked()
                        && s.getStartTime().isAfter(LocalDateTime.now().plusHours(1)))
                .skip(skip).findFirst().orElseThrow();
    }

    @Test
    void bookFreeSlot() {
        Slot s = freeSlot(0);
        var result = controller.book(s.getId(), REQ);
        assertEquals(s.getId(), result.id());
    }

    @Test
    void secondBookingOfSameSlotIsRejected() {
        Slot s = freeSlot(10);
        controller.book(s.getId(), REQ);
        var ex = assertThrows(ResponseStatusException.class,
                () -> controller.book(s.getId(), REQ));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void unknownSlotReturns404() {
        var ex = assertThrows(ResponseStatusException.class,
                () -> controller.book(Long.MAX_VALUE, REQ));
        assertEquals(404, ex.getStatusCode().value());
    }
}
