package lk.backhaulmatch.courier.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Customers")
@Getter
@Setter
@NoArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long customerId;

    private Long userId; // reference only — Users table lives in auth-service

    @ManyToOne
    @JoinColumn(name = "courier_company_id")
    private CourierCompany courierCompany;

    private String businessName;
}
