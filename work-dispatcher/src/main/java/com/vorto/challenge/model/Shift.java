package com.vorto.challenge.model;

import jakarta.persistence.*;
import org.geolatte.geom.Point;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shifts")
public class Shift {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne
    @JoinColumn(name = "driver_id", nullable = false, unique = true)
    private Driver driver;

    @Column(nullable = false, updatable = false)
    private Instant startTime;

    @Column
    private Instant endTime;

    @Column(nullable = false, columnDefinition = "geography(Point,4326)")
    private Point startLocation;
}
