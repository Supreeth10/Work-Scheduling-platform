package com.vorto.challenge.model;


import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "loads")
public class Load {

    public enum Status { AWAITING_DRIVER, RESERVED, IN_PROGRESS, COMPLETED }
    public enum StopKind { PICKUP, DROPOFF }

    @Id
    @GeneratedValue
    private UUID id;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(columnDefinition = "geometry(Point,4326)", nullable = false)
    private Point pickup;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(columnDefinition = "geometry(Point,4326)", nullable = false)
    private Point dropoff;



    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", columnDefinition = "load_status", nullable = false)
    private Status status = Status.AWAITING_DRIVER;



      @JdbcTypeCode(SqlTypes.NAMED_ENUM)
      @Column(name = "current_stop", columnDefinition = "stop_kind", nullable = false)
    private StopKind currentStop = StopKind.PICKUP;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_driver_id", foreignKey = @ForeignKey(name = "fk_loads_driver"))
    private Driver assignedDriver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_shift_id", foreignKey = @ForeignKey(name = "fk_loads_shift"))
    private Shift assignedShift;

    @Column(name = "reservation_expires_at")
    private Instant reservationExpiresAt;

    // ---- getters/setters ----
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Point getPickup() { return pickup; }
    public void setPickup(Point pickup) { this.pickup = pickup; }

    public Point getDropoff() { return dropoff; }
    public void setDropoff(Point dropoff) { this.dropoff = dropoff; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public StopKind getCurrentStop() { return currentStop; }
    public void setCurrentStop(StopKind currentStop) { this.currentStop = currentStop; }

    public Driver getAssignedDriver() { return assignedDriver; }
    public void setAssignedDriver(Driver assignedDriver) { this.assignedDriver = assignedDriver; }

    public Shift getAssignedShift() { return assignedShift; }
    public void setAssignedShift(Shift assignedShift) { this.assignedShift = assignedShift; }

    public Instant getReservationExpiresAt() { return reservationExpiresAt; }
    public void setReservationExpiresAt(Instant reservationExpiresAt) { this.reservationExpiresAt = reservationExpiresAt; }
}