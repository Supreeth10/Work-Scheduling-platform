//package com.vorto.challenge.model;
//
//import jakarta.persistence.*;
//import lombok.Getter;
//import lombok.Setter;
//import org.geolatte.geom.Point;
//
//import java.time.Instant;
//import java.util.UUID;
//
//@Getter
//@Setter
//@Entity
//@Table(name = "stop_events")
//public class StopEvent {
//
//    public enum Type {
//        PICKUP,
//        DROPOFF,
//        REJECT
//    }
//
//    @Id
//    @GeneratedValue
//    private UUID id;
//
//    @ManyToOne
//    @JoinColumn(name = "load_id", nullable = false)
//    private Load load;
//
//    @ManyToOne
//    @JoinColumn(name = "driver_id", nullable = false)
//    private Driver driver;
//
//    @Enumerated(EnumType.STRING)
//    @Column(nullable = false)
//    private Type type;
//
//    @Column(nullable = false)
//    private Instant at;
//
//    @Column(columnDefinition = "geography(Point,4326)")
//    private Point location;
//
//    @PrePersist
//    protected void onCreate() {
//        this.at = Instant.now();
//    }
//}
