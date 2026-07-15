package lk.backhaulmatch.courier.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "Shipment_Items")
@Getter
@Setter
@NoArgsConstructor
public class ShipmentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long itemId;

    @ManyToOne
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;

    private String itemDescription;
    private Integer quantity = 1;
    private BigDecimal weightKg;
}
