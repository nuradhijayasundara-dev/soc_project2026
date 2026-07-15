package lk.backhaulmatch.fleet.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Trucks")
@Getter
@Setter
@NoArgsConstructor
public class Truck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long truckId;

    @ManyToOne
    @JoinColumn(name = "fleet_company_id", nullable = false)
    private FleetCompany fleetCompany;

    @Column(nullable = false, unique = true, length = 20)
    private String truckNo; // e.g. WP-AB-1234

    private String truckType;

    @Column(nullable = false)
    private BigDecimal capacityTon;

    @Enumerated(EnumType.STRING)
    private Status status = Status.AVAILABLE;

    private LocalDateTime createdAt = LocalDateTime.now();

    public enum Status { AVAILABLE, IN_TRANSIT, MAINTENANCE, INACTIVE }
}
