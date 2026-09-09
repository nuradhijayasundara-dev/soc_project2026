package com.backhaulmatch.admin.controller;

import com.backhaulmatch.admin.dto.AdminDtos.DashboardSummaryResponse;
import com.backhaulmatch.admin.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.backhaulmatch.admin.controller.AdminGuard.requireAdmin;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> summary(@RequestHeader("X-User-Role") String role) {
        requireAdmin(role);
        return ResponseEntity.ok(dashboardService.summary());
    }
}
