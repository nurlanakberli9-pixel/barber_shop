package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DayPlannerTests {

    private static final LocalDate DAY = LocalDate.of(2030, 1, 1);

    private static DayPlanner.Span sp(int a, int b) {
        return new DayPlanner.Span(a, b);
    }

    @Test
    void mergeJoinsTouchingAndOverlappingSpans() {
        var merged = DayPlanner.merge(
                List.of(sp(800, 830), sp(630, 700), sp(600, 630), sp(820, 900)));
        assertEquals(List.of(sp(600, 700), sp(800, 900)), merged);
    }

    @Test
    void freeGapsSubtractBookings() {
        var gaps = DayPlanner.freeGaps(List.of(sp(600, 700)),
                List.of(sp(650, 660), sp(610, 640)));
        assertEquals(List.of(sp(600, 610), sp(640, 650), sp(660, 700)), gaps);
    }

    @Test
    void earliestFitAlignsToFiveMinutesAndSkipsSmallGaps() {
        assertEquals(605, DayPlanner.earliestFit(List.of(sp(603, 700)), 30));
        assertNull(DayPlanner.earliestFit(List.of(sp(660, 680)), 30));
        assertEquals(620, DayPlanner.earliestFit(List.of(sp(600, 610), sp(620, 700)), 30));
    }

    @Test
    void clipPastCutsTodayOnly() {
        var now = DAY.atTime(10, 20);
        assertEquals(List.of(sp(621, 700)),
                DayPlanner.clipPast(List.of(sp(600, 610), sp(600, 700)), DAY, now));
        assertEquals(List.of(sp(600, 700)),
                DayPlanner.clipPast(List.of(sp(600, 700)), DAY.plusDays(1), now));
        assertTrue(DayPlanner.clipPast(List.of(sp(600, 700)), DAY.minusDays(1), now).isEmpty());
    }

    @Test
    void fitMinutesIsTheLargestBookableChunk() {
        assertEquals(50, DayPlanner.fitMinutes(List.of(sp(603, 640), sp(650, 700))));
        assertEquals(0, DayPlanner.fitMinutes(List.of()));
    }
}
