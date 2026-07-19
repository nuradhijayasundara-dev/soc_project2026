package com.backhaulmatch.fleet.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "drivers")
@Data
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fleet_company_id", nullable = false)
    private Long fleetCompanyId;

    @Column(nullable = false)
    private String fullName;

    private String phone;
    private String licenseNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.AVAILABLE;

    public enum Status {
        AVAILABLE, ON_TRIP, OFF_DUTY
    }
}
