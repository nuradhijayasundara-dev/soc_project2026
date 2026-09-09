package com.backhaulmatch.fleet.controller;

import com.backhaulmatch.fleet.entity.FleetCompany;
import com.backhaulmatch.fleet.repository.FleetCompanyRepository;
import com.backhaulmatch.fleet.service.FleetCompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin-only endpoints for company approval. Living under "/internal/admin"
 * (NOT "/api/...") means the Gateway never exposes them to portal users —
 * only admin-service can reach them, calling fleet-service directly by its
 * Eureka name.
 */
@RestController
@RequestMapping("/internal/admin/companies")
@RequiredArgsConstructor
public class InternalAdminCompanyController {

    private final FleetCompanyRepository repository;
    private final FleetCompanyService companyService;

    @GetMapping
    public ResponseEntity<List<FleetCompany>> listAll() {
        return ResponseEntity.ok(repository.findAll());
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> count() {
        return ResponseEntity.ok(Map.of("count", repository.count()));
    }

    @PatchMapping("/{id}/approve")
    public ResponseEntity<FleetCompany> approve(@PathVariable Long id) {
        return ResponseEntity.ok(companyService.setApprovalStatus(id, FleetCompany.ApprovalStatus.APPROVED));
    }

    @PatchMapping("/{id}/reject")
    public ResponseEntity<FleetCompany> reject(@PathVariable Long id) {
        return ResponseEntity.ok(companyService.setApprovalStatus(id, FleetCompany.ApprovalStatus.REJECTED));
    }
}
