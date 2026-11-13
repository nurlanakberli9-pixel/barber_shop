package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class BookingControllerTests {

    @Autowired
    BookingController controller;

    @Test
    void bookFreeSlot() {
        var result = controller.book(3L,
                new BookingController.BookRequest("Elvin", "+994501234567"));
        assertEquals(3L, result.id());
    }

    @Test
    void secondBookingOfSameSlotIsRejected() {
        var req = new BookingController.BookRequest("Elvin", "+994501234567");
        controller.book(4L, req);
        var ex = assertThrows(ResponseStatusException.class, () -> controller.book(4L, req));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void unknownSlotReturns404() {
        var ex = assertThrows(ResponseStatusException.class, () -> controller.book(999L,
                new BookingController.BookRequest("Elvin", "+994501234567")));
        assertEquals(404, ex.getStatusCode().value());
    }
}
