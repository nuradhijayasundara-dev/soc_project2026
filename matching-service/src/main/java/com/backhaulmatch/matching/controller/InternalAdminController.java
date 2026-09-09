package com.backhaulmatch.matching.controller;

import com.backhaulmatch.matching.dto.ReportDtos.AdminSummaryResponse;
import com.backhaulmatch.matching.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin Dashboard data for admin-service. Living under "/internal/admin"
 * (NOT "/api/...") keeps it out of the Gateway's route table — only
 * admin-service can reach it, by Eureka name.
 */
@RestController
@RequestMapping("/internal/admin")
@RequiredArgsConstructor
public class InternalAdminController {

    private final ReportService reportService;

    @GetMapping("/reports/summary")
    public ResponseEntity<AdminSummaryResponse> adminSummary() {
        return ResponseEntity.ok(reportService.getAdminSummary());
    }
}
