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
    private Integer singleMinutes;
    private Integer lowMinutes;
    private Integer mediumMinutes;
    private Integer highMinutes;

    protected ServiceType() {}

    public ServiceType(Barbershop barbershop, ServiceKind kind) {
        this.barbershop = barbershop;
        this.kind = kind;
    }

    public Long getId() { return id; }
    public Barbershop getBarbershop() { return barbershop; }
    public ServiceKind getKind() { return kind; }
    public Integer getSingleMinutes() { return singleMinutes; }
    public Integer getLowMinutes() { return lowMinutes; }
    public Integer getMediumMinutes() { return mediumMinutes; }
    public Integer getHighMinutes() { return highMinutes; }

    public void setSingle(Integer minutes) {
        this.singleMinutes = minutes;
    }

    public void setLevels(Integer low, Integer medium, Integer high) {
        this.lowMinutes = low;
        this.mediumMinutes = medium;
        this.highMinutes = high;
    }

    public Integer minutesFor(Level level) {
        if (!kind.hasLevels()) return singleMinutes;
        if (level == null) return null;
        return switch (level) {
            case LOW -> lowMinutes;
            case MEDIUM -> mediumMinutes;
            case HIGH -> highMinutes;
        };
    }
}
