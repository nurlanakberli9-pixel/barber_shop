package com.barber.booking;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "closed_day",
        uniqueConstraints = @UniqueConstraint(columnNames = {"barbershop_id", "closed_date"}))
public class ClosedDay {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "barbershop_id")
    private Barbershop barbershop;
    @Column(name = "closed_date", nullable = false)
    private LocalDate closedDate;

    protected ClosedDay() {}

    public ClosedDay(Barbershop barbershop, LocalDate closedDate) {
        this.barbershop = barbershop;
        this.closedDate = closedDate;
    }

    public Long getId() { return id; }
    public Barbershop getBarbershop() { return barbershop; }
    public LocalDate getClosedDate() { return closedDate; }
}
