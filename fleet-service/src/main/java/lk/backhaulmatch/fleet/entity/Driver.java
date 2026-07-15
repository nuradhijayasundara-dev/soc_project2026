package lk.backhaulmatch.fleet.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Drivers")
@Getter
@Setter
@NoArgsConstructor
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long driverId;

    @Column(nullable = false, unique = true)
    private Long userId; // reference only — Users table lives in auth-service

    @ManyToOne
    @JoinColumn(name = "fleet_company_id", nullable = false)
    private FleetCompany fleetCompany;

    @Enumerated(EnumType.STRING)
    private Status status = Status.AVAILABLE;

    public enum Status { AVAILABLE, ON_TRIP, OFF_DUTY }
}
