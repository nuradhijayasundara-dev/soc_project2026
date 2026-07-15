package lk.backhaulmatch.courier.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Shipments")
@Getter
@Setter
@NoArgsConstructor
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long shipmentId;

    @Column(nullable = false, unique = true, length = 30)
    private String shipmentCode; // e.g. BK-2024-123

    @ManyToOne
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne
    @JoinColumn(name = "receiver_id", nullable = false)
    private Receiver receiver;

    @ManyToOne
    @JoinColumn(name = "courier_company_id", nullable = false)
    private CourierCompany courierCompany;

    private Integer pickupCityId;   // reference only — location-service
    private Integer deliveryCityId; // reference only — location-service

    private BigDecimal totalWeightKg;

    @Enumerated(EnumType.STRING)
    private Status status = Status.CREATED;

    private LocalDateTime createdAt = LocalDateTime.now();

    public enum Status { CREATED, ASSIGNED, IN_TRANSIT, DELIVERED, CANCELLED }
}
