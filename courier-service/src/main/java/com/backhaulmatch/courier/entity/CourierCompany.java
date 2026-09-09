package com.backhaulmatch.courier.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "courier_companies")
@Data
public class CourierCompany {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // References auth_db.users.id — the logged-in COURIER_OPERATOR that owns this company.
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private String companyName;

    private String registrationNo;
    private String contactPhone;
    private String address;

    // Admin approval workflow: new companies start PENDING and stay that way
    // until an admin approves them via the Admin Portal.
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false)
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public enum ApprovalStatus {
        PENDING, APPROVED, REJECTED
    }
}
