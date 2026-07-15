package lk.backhaulmatch.courier.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Courier_Branches")
@Getter
@Setter
@NoArgsConstructor
public class CourierBranch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer branchId;

    @ManyToOne
    @JoinColumn(name = "courier_company_id", nullable = false)
    private CourierCompany courierCompany;

    @Column(nullable = false, length = 150)
    private String branchName;

    private Integer cityId; // reference only — Cities table lives in location-service
    private String address;
    private String contactNumber;
}
