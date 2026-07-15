package lk.backhaulmatch.fleet.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "Vehicle_Maintenance")
@Getter
@Setter
@NoArgsConstructor
public class VehicleMaintenance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long maintenanceId;

    @ManyToOne
    @JoinColumn(name = "truck_id", nullable = false)
    private Truck truck;

    private String maintenanceType;
    private LocalDate scheduledDate;
    private LocalDate completedDate;
    private BigDecimal cost;
    private String remarks;
}
