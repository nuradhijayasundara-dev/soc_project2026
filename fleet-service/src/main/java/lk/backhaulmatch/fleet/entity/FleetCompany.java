package lk.backhaulmatch.fleet.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "Fleet_Companies")
@Getter
@Setter
@NoArgsConstructor
public class FleetCompany {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer fleetCompanyId;

    @Column(nullable = false, length = 150)
    private String companyName;

    @Column(unique = true, length = 50)
    private String registrationNo;

    private Long ownerUserId; // reference only — Users table lives in auth-service

    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;

    private LocalDateTime createdAt = LocalDateTime.now();

    public enum Status { ACTIVE, INACTIVE }
}
