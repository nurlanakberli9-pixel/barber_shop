package com.barber.booking;

import jakarta.persistence.*;

@Entity
@Table(name = "service_type",
        uniqueConstraints = @UniqueConstraint(columnNames = {"barbershop_id", "kind"}))
public class ServiceType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "barbershop_id")
    private Barbershop barbershop;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServiceKind kind;
    private Integer durationMinutes;

    protected ServiceType() {}

    public ServiceType(Barbershop barbershop, ServiceKind kind) {
        this.barbershop = barbershop;
        this.kind = kind;
    }

    public Long getId() { return id; }
    public Barbershop getBarbershop() { return barbershop; }
    public ServiceKind getKind() { return kind; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
}
