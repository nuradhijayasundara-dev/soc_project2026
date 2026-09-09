package com.backhaulmatch.matching.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One row per "find me a truck for this shipment" request from the Courier Portal. */
@Entity
@Table(name = "match_requests")
@Data
public class MatchRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // References courier_db.shipments.id — validated via courier-service at creation time.
    @Column(name = "shipment_id", nullable = false)
    private Long shipmentId;

    @Column(name = "requested_by_user_id", nullable = false)
    private Long requestedByUserId;

    // Snapshot of the shipment at request time, so matching logic doesn't need
    // to re-call courier-service for every comparison.
    @Column(nullable = false)
    private String pickupLocation;

    @Column(nullable = false)
    private String destination;

    private BigDecimal weightKg;

    // What the courier asked for — required vehicle type and priority are
    // captured here so scoring/estimating doesn't need another round trip,
    // and so admin can later pre-filter fleet trucks by type if wanted.
    private String requiredVehicleType;
    private String priority;

    // Snapshot of the shipment's pickup time. Used for date/time compatibility
    // when matching against truck availability, and as the expiry signal for a
    // WAITING_FOR_MATCH request — once the pickup time has passed with no truck
    // found, waiting is pointless and the request is settled.
    @Column(name = "pickup_datetime")
    private LocalDateTime pickupDatetime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public enum Status {
        PENDING,   // request created, results generated, awaiting selection
        MATCH_FOUND, // engine found candidate trucks (results list is populated)
        MATCHED,   // a result has been selected
        WAITING_FOR_MATCH, // matching ran but found nothing — kept active for later rechecks
        NO_MATCH,  // wait window elapsed (expired) with no suitable truck found
        CANCELLED
    }
}
