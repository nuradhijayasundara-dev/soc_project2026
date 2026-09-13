package com.backhaulmatch.admin.service;

import com.backhaulmatch.admin.client.AuthServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Thin wrapper around auth-service's internal user-management endpoints. */
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final AuthServiceClient authServiceClient;
    private final AdminAuditService auditService;

    public List<Map<String, Object>> listUsers() {
        return authServiceClient.listUsers();
    }

    public Map<String, Object> createUser(String adminUsername, String username, String email,
                                          String password, String role) {
        Map<String, Object> created = authServiceClient.createUser(username, email, password, role);
        auditService.record("USER_CREATED", (Long) created.get("id"), username, "create user",
                "Admin " + adminUsername + " created " + role + " account", "admin-service");
        return created;
    }

    public Map<String, Object> updateUser(String adminUsername, Long id, String username,
                                          String email, String password, String role) {
        Map<String, Object> updated = authServiceClient.updateUser(id, username, email, password, role);
        auditService.record("USER_UPDATED", id, String.valueOf(updated.getOrDefault("username", username)), "update user",
                "Admin " + adminUsername + " edited the account", "admin-service");
        return updated;
    }

    public Map<String, Object> setUserEnabled(String adminUsername, Long id, boolean enabled) {
        Map<String, Object> updated = authServiceClient.setUserEnabled(id, enabled);
        String username = String.valueOf(updated.getOrDefault("username", id));
        auditService.record("USER_STATUS", id, username, enabled ? "activate user" : "deactivate user",
                "Admin " + adminUsername + " set the account " + (enabled ? "active" : "inactive"), "admin-service");
        return updated;
    }

    public Map<String, Object> setApproval(String adminUsername, Long id, String status) {
        Map<String, Object> updated = authServiceClient.setApprovalStatus(id, status);
        String username = String.valueOf(updated.getOrDefault("username", id));
        auditService.record("USER_APPROVAL", id, username, status.equalsIgnoreCase("APPROVED") ? "approve user" : "reject user",
                "Admin " + adminUsername + " " + status.toLowerCase() + " the registration of " + username, "admin-service");
        return updated;
    }

    public void deleteUser(String adminUsername, Long id, String username) {
        authServiceClient.deleteUser(id);
        auditService.record("USER_DELETED", id, username, "delete user",
                "Admin " + adminUsername + " deleted the account", "admin-service");
    }
}
