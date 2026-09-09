package com.backhaulmatch.admin.controller;

import com.backhaulmatch.admin.service.UserAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static com.backhaulmatch.admin.controller.AdminGuard.requireAdmin;

/**
 * User Management — Add / Edit / Delete / Activate-Deactivate. All calls fan
 * out to auth-service's internal admin endpoints.
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list(@RequestHeader("X-User-Role") String role,
                                                          @RequestHeader(value = "X-User-Id", required = false) String userId) {
        requireAdmin(role);
        return ResponseEntity.ok(userAdminService.listUsers());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestHeader("X-User-Role") String role,
                                                      @RequestHeader(value = "X-User-Username", required = false) String adminUsername,
                                                      @RequestBody Map<String, String> body) {
        requireAdmin(role);
        Map<String, Object> created = userAdminService.createUser(adminUsername,
                body.get("username"), body.get("email"), body.get("password"), body.get("role"));
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@RequestHeader("X-User-Role") String role,
                                                      @RequestHeader(value = "X-User-Username", required = false) String adminUsername,
                                                      @PathVariable Long id,
                                                      @RequestBody Map<String, String> body) {
        requireAdmin(role);
        return ResponseEntity.ok(userAdminService.updateUser(adminUsername, id,
                body.get("username"), body.get("email"), body.get("password"), body.get("role")));
    }

    @PatchMapping("/{id}/enabled")
    public ResponseEntity<Map<String, Object>> setEnabled(@RequestHeader("X-User-Role") String role,
                                                          @RequestHeader(value = "X-User-Username", required = false) String adminUsername,
                                                          @PathVariable Long id,
                                                          @RequestParam boolean enabled) {
        requireAdmin(role);
        return ResponseEntity.ok(userAdminService.setUserEnabled(adminUsername, id, enabled));
    }

    @PatchMapping("/{id}/approval")
    public ResponseEntity<Map<String, Object>> setApproval(@RequestHeader("X-User-Role") String role,
                                                           @RequestHeader(value = "X-User-Username", required = false) String adminUsername,
                                                           @PathVariable Long id,
                                                           @RequestParam String status) {
        requireAdmin(role);
        String normalized = status.toUpperCase();
        if (!"APPROVED".equals(normalized) && !"REJECTED".equals(normalized)) {
            throw new IllegalArgumentException("status must be APPROVED or REJECTED");
        }
        return ResponseEntity.ok(userAdminService.setApproval(adminUsername, id, normalized));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestHeader("X-User-Role") String role,
                                       @RequestHeader(value = "X-User-Username", required = false) String adminUsername,
                                       @PathVariable Long id,
                                       @RequestParam(required = false) String username) {
        requireAdmin(role);
        userAdminService.deleteUser(adminUsername, id, username);
        return ResponseEntity.noContent().build();
    }
}
