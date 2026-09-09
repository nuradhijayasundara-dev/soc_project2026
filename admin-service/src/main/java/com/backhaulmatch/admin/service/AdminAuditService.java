package com.backhaulmatch.admin.service;

import com.backhaulmatch.admin.entity.AuditLog;
import com.backhaulmatch.admin.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private final AuditLogRepository repository;

    /** Called by the Gateway, auth-service (service-to-service) and this service's own actions. */
    public AuditLog record(String type, Long userId, String username, String action, String details, String source) {
        AuditLog log = new AuditLog();
        log.setType(type);
        log.setUserId(userId);
        log.setUsername(username);
        log.setAction(action);
        log.setDetails(details);
        log.setSource(source);
        return repository.save(log);
    }

    public List<AuditLog> query(String type, Long userId, LocalDateTime from, LocalDateTime to) {
        List<AuditLog> logs = (type != null && !type.isBlank())
                ? (userId != null
                    ? repository.findByTypeAndUserIdOrderByCreatedAtDesc(type, userId)
                    : repository.findByTypeOrderByCreatedAtDesc(type))
                : (userId != null
                    ? repository.findByUserIdOrderByCreatedAtDesc(userId)
                    : repository.findAllByOrderByCreatedAtDesc());

        if (from == null && to == null) return logs;
        return logs.stream()
                .filter(l -> from == null || !l.getCreatedAt().isBefore(from))
                .filter(l -> to == null || !l.getCreatedAt().isAfter(to))
                .toList();
    }
}
