package lk.backhaulmatch.fleet.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "Driver_Licenses")
@Getter
@Setter
@NoArgsConstructor
public class DriverLicense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long licenseId;

    @ManyToOne
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @Column(nullable = false, unique = true, length = 30)
    private String licenseNo;

    private String licenseClass;
    private LocalDate issuedDate;

    @Column(nullable = false)
    private LocalDate expiryDate;
}
