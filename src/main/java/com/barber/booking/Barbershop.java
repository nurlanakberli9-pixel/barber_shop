package com.barber.booking;

import jakarta.persistence.*;

@Entity
public class Barbershop {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private String address;

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
}
