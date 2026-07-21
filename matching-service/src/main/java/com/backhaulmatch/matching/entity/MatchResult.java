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
    private BigDecimal availableCapacityTon;
    private String routeFrom;
    private String routeTo;

    private BigDecimal estimatedCost;

    // Lower = better fit. Combines capacity efficiency (least wasted capacity)
    // and route/time proximity — see MatchingService for the exact formula.
    private Double matchScore;

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
        RECOMMENDED, SELECTED, REJECTED
    }
}
