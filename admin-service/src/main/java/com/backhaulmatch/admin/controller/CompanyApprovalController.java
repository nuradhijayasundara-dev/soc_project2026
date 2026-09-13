package com.backhaulmatch.admin.controller;

import com.backhaulmatch.admin.dto.AdminDtos.CompanyListResponse;
import com.backhaulmatch.admin.dto.AdminDtos.CompanyView;
import com.backhaulmatch.admin.service.CompanyApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.backhaulmatch.admin.controller.AdminGuard.requireAdmin;

@RestController
@RequestMapping("/api/admin/companies")
@RequiredArgsConstructor
public class CompanyApprovalController {

    private final CompanyApprovalService companyApprovalService;

    @GetMapping
    public ResponseEntity<CompanyListResponse> list(@RequestHeader("X-User-Role") String role) {
        requireAdmin(role);
        return ResponseEntity.ok(companyApprovalService.listCompanies());
    }

    @PatchMapping("/{type}/{id}/approve")
    public ResponseEntity<CompanyView> approve(@RequestHeader("X-User-Role") String role,
                                               @RequestHeader(value = "X-User-Username", required = false) String adminUsername,
                                               @PathVariable String type, @PathVariable Long id) {
        requireAdmin(role);
        return ResponseEntity.ok(companyApprovalService.approve(type, id, adminUsername));
    }

    @PatchMapping("/{type}/{id}/reject")
    public ResponseEntity<CompanyView> reject(@RequestHeader("X-User-Role") String role,
                                              @RequestHeader(value = "X-User-Username", required = false) String adminUsername,
                                              @PathVariable String type, @PathVariable Long id) {
        requireAdmin(role);
        return ResponseEntity.ok(companyApprovalService.reject(type, id, adminUsername));
    }
}
