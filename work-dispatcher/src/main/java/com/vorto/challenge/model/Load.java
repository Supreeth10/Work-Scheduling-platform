package com.vorto.challenge.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.geolatte.geom.Point;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "loads")
public class Load {

    public enum Status {
        AWAITING_DRIVER,
        IN_PROGRESS,
        COMPLETED
    }

    public enum CurrentStop {
        PICKUP,
        DROPOFF
    }

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.AWAITING_DRIVER;

    @Enumerated(EnumType.STRING)
    private CurrentStop currentStop = CurrentStop.PICKUP;

    @Column(nullable = false, columnDefinition = "geography(Point,4326)")
    private Point pickup;

    @Column(nullable = false, columnDefinition = "geography(Point,4326)")
    private Point dropoff;

    @ManyToOne
    @JoinColumn(name = "driver_id")
    private Driver assignedDriver;

    @ManyToOne
    @JoinColumn(name = "shift_id")
    private Shift assignedShift;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
