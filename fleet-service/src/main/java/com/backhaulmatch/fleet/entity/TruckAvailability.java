package com.backhaulmatch.fleet.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "truck_availability")
@Data
public class TruckAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "truck_id", nullable = false)
    private Long truckId;

    private Long driverId;

    @Column(name = "route_from", nullable = false)
    private String routeFrom;

    @Column(name = "route_to", nullable = false)
    private String routeTo;

    private String startLocationName;
    private Double startLatitude;
    private Double startLongitude;
    private String destinationName;
    private Double destinationLatitude;
    private Double destinationLongitude;
    private BigDecimal distanceKm;
    private String estimatedDuration;

    @Column(name = "available_from", nullable = false)
    private LocalDateTime availableFrom;

    // When the truck is expected to arrive at routeTo — departure (availableFrom)
    // + the driving duration of the posted route. Shown to couriers on the
    // match-results screen so they know how long cargo will actually be on the road.
    @Column(name = "expected_arrival")
    private LocalDateTime expectedArrival;

    @Column(name = "available_capacity_ton", nullable = false)
    private BigDecimal availableCapacityTon;

    // Most postings on this platform ARE backhaul (empty return leg) — this flag
    // just makes that explicit for reporting/filtering, and defaults accordingly.
    @Enumerated(EnumType.STRING)
    @Column(name = "trip_type", nullable = false)
    private TripType tripType = TripType.BACKHAUL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.AVAILABLE;

    public enum TripType {
        OUTBOUND, BACKHAUL
    }

    public enum Status {
        AVAILABLE, BOOKED, EXPIRED
    }
}
