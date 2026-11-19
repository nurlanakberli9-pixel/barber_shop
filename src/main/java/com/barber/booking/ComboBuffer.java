package com.barber.booking;

import jakarta.persistence.*;

@Entity
@Table(name = "combo_buffer",
        uniqueConstraints = @UniqueConstraint(columnNames = {"barbershop_id", "combo_key"}))
public class ComboBuffer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "barbershop_id")
    private Barbershop barbershop;
    @Column(name = "combo_key", nullable = false, length = 40)
    private String comboKey;
    @Column(name = "after_minutes", nullable = false)
    private int afterMinutes;

    protected ComboBuffer() {}

    public ComboBuffer(Barbershop barbershop, String comboKey, int afterMinutes) {
        this.barbershop = barbershop;
        this.comboKey = comboKey;
        this.afterMinutes = afterMinutes;
    }

    public Long getId() { return id; }
    public String getComboKey() { return comboKey; }
    public int getAfterMinutes() { return afterMinutes; }
}
