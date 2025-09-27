package com.vorto.challenge.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Point;

import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "drivers")
public class Driver {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    // Store current location as a PostGIS geography(Point, 4326)
    @Column(columnDefinition = "geography(Point,4326)")
    private Point currentLocation;

    @Column(nullable = false)
    private boolean onShift = false;

    @OneToOne(mappedBy = "driver", cascade = CascadeType.ALL)
    private Shift shift;
}
