package com.barber.booking;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "work_window")
public class WorkWindow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "barbershop_id")
    private Barbershop barbershop;
    @Column(name = "window_date", nullable = false)
    private LocalDate windowDate;
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;
    @OneToMany(mappedBy = "window", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<WindowCube> cubes = new ArrayList<>();

    protected WorkWindow() {}

    public WorkWindow(Barbershop barbershop, LocalDate windowDate, LocalTime startTime) {
        this.barbershop = barbershop;
        this.windowDate = windowDate;
        this.startTime = startTime;
    }

    public void addCube(ServiceKind kind, int minutes) {
        cubes.add(new WindowCube(this, kind, minutes, cubes.size()));
    }

    public int totalMinutes() {
        return cubes.stream().mapToInt(WindowCube::getMinutes).sum();
    }

    public LocalTime getEndTime() {
        return startTime.plusMinutes(totalMinutes());
    }

    public Long getId() { return id; }
    public Barbershop getBarbershop() { return barbershop; }
    public LocalDate getWindowDate() { return windowDate; }
    public LocalTime getStartTime() { return startTime; }
    public List<WindowCube> getCubes() { return cubes; }
}
