package com.backhaulmatch.courier.controller;

import com.backhaulmatch.courier.entity.CourierCompany;
import com.backhaulmatch.courier.repository.CourierCompanyRepository;
import com.backhaulmatch.courier.service.CourierCompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin-only endpoints for company approval. Living under "/internal/admin"
 * (NOT "/api/...") means the Gateway never exposes them to portal users —
 * only admin-service can reach them, calling courier-service directly by its
 * Eureka name.
 */
@RestController
@RequestMapping("/internal/admin/companies")
@RequiredArgsConstructor
public class InternalAdminCompanyController {

    private final CourierCompanyRepository repository;
    private final CourierCompanyService companyService;

    @GetMapping
    public ResponseEntity<List<CourierCompany>> listAll() {
        return ResponseEntity.ok(repository.findAll());
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> count() {
        return ResponseEntity.ok(Map.of("count", repository.count()));
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<CourierCompany> approve(@PathVariable Long id) {
        return ResponseEntity.ok(companyService.setApprovalStatus(id, CourierCompany.ApprovalStatus.APPROVED));
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<CourierCompany> reject(@PathVariable Long id) {
        return ResponseEntity.ok(companyService.setApprovalStatus(id, CourierCompany.ApprovalStatus.REJECTED));
    }
}
