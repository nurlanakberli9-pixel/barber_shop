package com.barber.booking;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"barbershop_id", "start_time"}))
public class Slot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "barbershop_id")
    private Barbershop barbershop;
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;
    @Column(nullable = false)
    private boolean booked;
    private String customerName;
    private String customerPhone;
    @Version
    private Long version;

    protected Slot() {}

    public Slot(Barbershop barbershop, LocalDateTime startTime) {
        this.barbershop = barbershop;
        this.startTime = startTime;
    }

    public Long getId() { return id; }
    public Barbershop getBarbershop() { return barbershop; }
    public LocalDateTime getStartTime() { return startTime; }
    public boolean isBooked() { return booked; }
    public String getCustomerName() { return customerName; }
    public String getCustomerPhone() { return customerPhone; }

    public void book(String name, String phone) {
        this.booked = true;
        this.customerName = name;
        this.customerPhone = phone;
    }
}
