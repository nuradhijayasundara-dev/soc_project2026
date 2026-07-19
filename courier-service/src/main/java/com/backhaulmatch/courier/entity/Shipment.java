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

    @Column(nullable = false)
    private LocalDateTime pickupDatetime;

    @Column(nullable = false)
    private String destination;

    private LocalDate deliveryDeadline;
    private BigDecimal weightKg;
    private String dimensions;
    private String parcelType;
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
        PENDING, MATCHED, IN_TRANSIT, DELIVERED, CANCELLED
    }
}
