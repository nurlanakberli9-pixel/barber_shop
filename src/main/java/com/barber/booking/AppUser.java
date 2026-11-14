package com.barber.booking;

import jakarta.persistence.*;

@Entity
@Table(name = "app_user")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String username;
    @Column(nullable = false)
    private String passwordHash;
    @Column(nullable = false)
    private String role;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "barbershop_id", unique = true)
    private Barbershop barbershop;

    protected AppUser() {}

    public AppUser(String username, String passwordHash, String role, Barbershop barbershop) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.barbershop = barbershop;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public Barbershop getBarbershop() { return barbershop; }
}
