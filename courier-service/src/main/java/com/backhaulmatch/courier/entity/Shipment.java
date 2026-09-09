package com.backhaulmatch.courier.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "shipments")
@Data
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shipment_code", nullable = false, unique = true)
    private String shipmentCode; // e.g. SHP-0001, generated server-side

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    // Denormalized company id so we can filter "my shipments" without a join to customers.
    @Column(name = "courier_company_id", nullable = false)
    private Long courierCompanyId;

    @Column(nullable = false)
    private String pickupLocation;

    private String pickupName;
    private Double pickupLatitude;
    private Double pickupLongitude;

    @Column(nullable = false)
    private LocalDateTime pickupDatetime;

    @Column(nullable = false)
    private String destination;

    private String destinationName;
    private Double destinationLatitude;
    private Double destinationLongitude;

    private BigDecimal distanceKm;
    private String estimatedDuration;

    private LocalDate deliveryDeadline;
    private BigDecimal weightKg;
    private String dimensions;
    private String parcelType;

    // The courier states what kind of truck the load needs and how urgent it is —
    // both feed the price estimate and can be used to pre-filter matched trucks.
    private String requiredVehicleType; // STANDARD | REFRIGERATED | FLATBED | BOX_TRUCK | TANKER
    private String priority;            // NORMAL | PRIORITY | URGENT

    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;

    private BigDecimal estimatedCost;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public enum Status {
        // Courier created the shipment — waiting for the courier to request backhaul transport.
        PENDING,
        // Matching engine is actively searching / has been requested.
        MATCHING,
        // Compatible backhaul trucks were found — the Courier Portal shows the ranked list.
        MATCH_FOUND,
        // (kept for backwards compatibility with the old flow)
        MATCHED,
        // Courier picked a specific truck → capacity reserved, awaiting the fleet manager's decision.
        BOOKING_PENDING,
        // Fleet manager accepted → the slot is locked, a trip and invoice were created.
        CONFIRMED,
        // Fleet assigned a driver to the trip.
        DRIVER_ASSIGNED,
        // Trip started → the truck is moving. GPS tracking is live.
        IN_TRANSIT,
        // Truck arrived and marked the delivery complete.
        DELIVERED,
        // Delivery is fully closed out.
        COMPLETED,
        // Matching ran but found nothing suitable yet — the shipment stays active
        // and the engine keeps looking whenever a fleet manager posts new
        // availability, until a truck is found, the courier cancels it, or it expires.
        WAITING_FOR_MATCH,
        // Matching found no suitable trucks and the wait window elapsed (expired) —
        // this is the terminal "no luck" state, distinct from WAITING_FOR_MATCH.
        NO_MATCH,
        REJECTED,
        CANCELLED
    }
}
