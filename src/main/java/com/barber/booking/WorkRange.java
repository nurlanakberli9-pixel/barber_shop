package com.barber.booking;

import jakarta.persistence.*;

@Entity
@Table(name = "work_range")
public class WorkRange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "barbershop_id")
    private Barbershop barbershop;
    @Column(name = "start_minute", nullable = false)
    private int startMinute;
    @Column(name = "end_minute", nullable = false)
    private int endMinute;

    protected WorkRange() {}

    public WorkRange(Barbershop barbershop, int startMinute, int endMinute) {
        this.barbershop = barbershop;
        this.startMinute = startMinute;
        this.endMinute = endMinute;
    }

    public Long getId() { return id; }
    public int getStartMinute() { return startMinute; }
    public int getEndMinute() { return endMinute; }
}
