package com.barber.booking;

import java.time.LocalDate;

public record BookingCreatedEvent(Long shopId, LocalDate date, int start, int end) {}
