package com.backhaulmatch.matching.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One row per candidate truck the Matching Engine found for a MatchRequest. */
@Entity
@Table(name = "match_results")
@Data
public class MatchResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "match_request_id", nullable = false)
    private Long matchRequestId;

    // References fleet_db.truck_availability.id / trucks.id — resolved via fleet-service.
    @Column(name = "truck_availability_id", nullable = false)
    private Long truckAvailabilityId;

    @Column(name = "truck_id", nullable = false)
    private Long truckId;

    @Column(name = "fleet_company_id", nullable = false)
    private Long fleetCompanyId;

    // Denormalized fields so the Courier Portal's results screen doesn't need
    // a second round-trip to fleet-service to render the list.
    private String truckNo;
    private String truckType;
    private BigDecimal availableCapacityTon;
    private String routeFrom;
    private String routeTo;

    // The fleet company behind this truck (denormalized for display on the
    // match-results screen without an extra service call per row).
    private String fleetCompanyName;

    // When the truck is expected to arrive at routeTo — shown to the courier so
    // they know how long cargo will be on the road.
    private LocalDateTime expectedArrival;

    // Shipment weight at match time — lets both the courier's results screen
    // and the fleet's booking-request screen show "X kg vs Y ton capacity"
    // per row without re-fetching the shipment.
    private BigDecimal weightKg;

    // Great-circle deviation between the shipment's actual route and this
    // truck's posted route (see DistanceCalculator) — 0 = exact match, and it
    // falls back to a fixed penalty distance for any city not in the lookup
    // table rather than being left null. Feeds into both matchScore and,
    // via the shipment's own route distance, estimatedCost.
    private Double distanceKm;

    private BigDecimal estimatedCost;

    // Lower = better fit. Combines capacity efficiency (least wasted capacity)
    // and route/time proximity — see MatchingService for the exact formula.
    private Double matchScore;

    // True only on the single top-scoring candidate per request — the engine's
    // auto-selected best match. The rest of the list are alternatives the
    // courier can still review and book instead.
    @Column(name = "is_best_match", nullable = false)
    private Boolean isBestMatch = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.RECOMMENDED;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public enum Status {
        RECOMMENDED,          // matching engine's suggestion, not yet acted on
        PENDING_CONFIRMATION, // courier clicked "Accept Match" — capacity reserved, waiting on the fleet manager
        ACCEPTED,              // fleet manager accepted — this is the booking
        REJECTED               // fleet manager declined, or auto-rejected as a sibling of an accepted one
    }
}
