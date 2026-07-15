package lk.backhaulmatch.courier.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "Shipment_Tracking")
@Getter
@Setter
@NoArgsConstructor
public class ShipmentTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long trackingId;

    @ManyToOne
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    @Column(nullable = false, length = 50)
    private String status;

    private String location;
    private String remarks;
    private LocalDateTime updatedAt = LocalDateTime.now();
}
