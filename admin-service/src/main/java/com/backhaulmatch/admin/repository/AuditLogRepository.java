package com.backhaulmatch.admin.repository;

import com.backhaulmatch.admin.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findAllByOrderByCreatedAtDesc();
    List<AuditLog> findByTypeOrderByCreatedAtDesc(String type);
    List<AuditLog> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<AuditLog> findByTypeAndUserIdOrderByCreatedAtDesc(String type, Long userId);
}
