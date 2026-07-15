package lk.backhaulmatch.fleet.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "Truck_Availability")
@Getter
@Setter
@NoArgsConstructor
public class TruckAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long availabilityId;

    @ManyToOne
    @JoinColumn(name = "truck_id", nullable = false)
    private Truck truck;

    private Integer routeFromCityId; // reference only — location-service
    private Integer routeToCityId;   // reference only — location-service

    @Column(nullable = false)
    private BigDecimal availableCapacityTon;

    @Column(nullable = false)
    private LocalDateTime availableFrom;

    private LocalDateTime availableTo;

    @Enumerated(EnumType.STRING)
    private Status status = Status.AVAILABLE;

    public enum Status { AVAILABLE, BOOKED, EXPIRED }
}
