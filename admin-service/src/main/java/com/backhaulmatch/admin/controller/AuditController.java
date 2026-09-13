package com.backhaulmatch.admin.controller;

import com.backhaulmatch.admin.dto.AdminDtos.InternalAuditRequest;
import com.backhaulmatch.admin.entity.AuditLog;
import com.backhaulmatch.admin.service.AdminAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

import static com.backhaulmatch.admin.controller.AdminGuard.requireAdmin;

@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AdminAuditService auditService;

    // Ingest endpoint for the Gateway + auth-service (service-to-service, Eureka name).
    // No admin guard: it's only reachable outside the Gateway's route table.
    @PostMapping("/internal")
    public ResponseEntity<AuditLog> ingest(@RequestBody InternalAuditRequest request) {
        return ResponseEntity.ok(auditService.record(
                request.type(), request.userId(), request.username(), request.action(),
                request.details(), request.source() == null ? "service" : request.source()));
    }

    @GetMapping
    public ResponseEntity<List<AuditLog>> query(@RequestHeader("X-User-Role") String role,
                                                @RequestParam(required = false) String type,
                                                @RequestParam(required = false) Long userId,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        requireAdmin(role);
        return ResponseEntity.ok(auditService.query(type, userId, from, to));
    }

    @GetMapping("/login-history")
    public ResponseEntity<List<AuditLog>> loginHistory(@RequestHeader("X-User-Role") String role,
                                                       @RequestParam(required = false) String type,
                                                       @RequestParam(required = false) Long userId) {
        requireAdmin(role);
        List<AuditLog> logs = auditService.query(type, userId, null, null);
        return ResponseEntity.ok(logs.stream()
                .filter(l -> "LOGIN".equals(l.getType()) || "FAILED_LOGIN".equals(l.getType()))
                .toList());
    }
}
