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
    private String masterName;
    @Column(nullable = false)
    private String address;
    @Column(nullable = false)
    private double rating;

    protected Barbershop() {}

    public Barbershop(String name, String masterName, String address, double rating) {
        this.name = name;
        this.masterName = masterName;
        this.address = address;
        this.rating = rating;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getMasterName() { return masterName; }
    public String getAddress() { return address; }
    public double getRating() { return rating; }
}
