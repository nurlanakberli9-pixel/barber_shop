package com.barber.booking;

import jakarta.persistence.*;

@Entity
@Table(name = "window_cube")
public class WindowCube {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "window_id")
    private WorkWindow window;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServiceKind kind;
    @Column(nullable = false)
    private int minutes;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected WindowCube() {}

    public WindowCube(WorkWindow window, ServiceKind kind, int minutes, int sortOrder) {
        this.window = window;
        this.kind = kind;
        this.minutes = minutes;
        this.sortOrder = sortOrder;
    }

    public Long getId() { return id; }
    public WorkWindow getWindow() { return window; }
    public ServiceKind getKind() { return kind; }
    public int getMinutes() { return minutes; }
    public int getSortOrder() { return sortOrder; }
}
