package com.vorto.challenge.model;

import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "drivers")
public class Driver {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(columnDefinition = "geometry(Point,4326)")
    private Point currentLocation;

    @Column(nullable = false)
    private boolean onShift = false;

    @OneToMany(mappedBy = "driver", cascade = CascadeType.ALL, orphanRemoval = false)
    private List<Shift> shifts = new ArrayList<>();

    @Column(name = "planned_next_load_id")
    private UUID plannedNextLoadId;

    public List<Shift> getShifts() {
        return shifts;
    }

    public void setShifts(List<Shift> shifts) {
        this.shifts = shifts;
    }

    public UUID getPlannedNextLoadId() {
        return plannedNextLoadId;
    }

    public void setPlannedNextLoadId(UUID plannedNextLoadId) {
        this.plannedNextLoadId = plannedNextLoadId;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Point getCurrentLocation() {
        return currentLocation;
    }

    public void setCurrentLocation(Point currentLocation) {
        this.currentLocation = currentLocation;
    }

    public boolean isOnShift() {
        return onShift;
    }

    public void setOnShift(boolean onShift) {
        this.onShift = onShift;
    }
}
