package com.barber.booking;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "push_subscription")
public class PushSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "barbershop_id")
    private Barbershop barbershop;
    @Column(nullable = false, unique = true, length = 600)
    private String endpoint;
    @Column(nullable = false, length = 200)
    private String p256dh;
    @Column(nullable = false, length = 100)
    private String auth;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected PushSubscription() {}

    public PushSubscription(Barbershop barbershop, String endpoint, String p256dh, String auth) {
        this.barbershop = barbershop;
        this.endpoint = endpoint;
        this.p256dh = p256dh;
        this.auth = auth;
        this.createdAt = LocalDateTime.now();
    }

    public void update(Barbershop barbershop, String p256dh, String auth) {
        this.barbershop = barbershop;
        this.p256dh = p256dh;
        this.auth = auth;
    }

    public Long getId() { return id; }
    public Barbershop getBarbershop() { return barbershop; }
    public String getEndpoint() { return endpoint; }
    public String getP256dh() { return p256dh; }
    public String getAuth() { return auth; }
}
