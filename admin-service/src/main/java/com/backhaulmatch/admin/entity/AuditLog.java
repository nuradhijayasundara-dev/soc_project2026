package com.backhaulmatch.admin.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** One row per audit event — login, user change, company approval, API call. */
@Entity
@Table(name = "audit_logs")
@Data
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // LOGIN | FAILED_LOGIN | USER_REGISTERED | USER_CREATED | USER_UPDATED |
    // USER_DELETED | USER_STATUS | COMPANY_APPROVED | COMPANY_REJECTED |
    // SETTING_UPDATED | API_CALL | ...
    @Column(nullable = false)
    private String type;

    // References auth_db.users.id — may be null (e.g. failed login, gateway API call).
    @Column(name = "user_id")
    private Long userId;

    private String username;

    @Column(nullable = false)
    private String action;

    @Column(length = 1000)
    private String details;

    // Which component recorded it: auth-service | admin-service | gateway
    private String source;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
