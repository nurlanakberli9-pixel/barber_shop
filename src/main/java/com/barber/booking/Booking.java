package com.barber.booking;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Table(name = "booking",
        indexes = @Index(name = "idx_booking_shop_date", columnList = "barbershop_id,booking_date"))
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "barbershop_id")
    private Barbershop barbershop;
    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;
    @Column(name = "start_minute", nullable = false)
    private int startMinute;
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;
    @Column(nullable = false, length = 64)
    private String services;
    @Column(name = "customer_name", nullable = false, length = 60)
    private String customerName;
    @Column(name = "customer_phone", nullable = false, length = 16)
    private String customerPhone;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Booking() {}

    public Booking(Barbershop barbershop, LocalDate bookingDate, int startMinute,
            int durationMinutes, List<ServiceKind> kinds, String customerName,
            String customerPhone) {
        this.barbershop = barbershop;
        this.bookingDate = bookingDate;
        this.startMinute = startMinute;
        this.durationMinutes = durationMinutes;
        this.services = kinds.stream().map(Enum::name).collect(Collectors.joining(","));
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Barbershop getBarbershop() { return barbershop; }
    public LocalDate getBookingDate() { return bookingDate; }
    public int getStartMinute() { return startMinute; }
    public int getDurationMinutes() { return durationMinutes; }
    public int getEndMinute() { return startMinute + durationMinutes; }
    public String getCustomerName() { return customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public List<ServiceKind> getKinds() {
        return Arrays.stream(services.split(",")).map(ServiceKind::valueOf).toList();
    }
}
