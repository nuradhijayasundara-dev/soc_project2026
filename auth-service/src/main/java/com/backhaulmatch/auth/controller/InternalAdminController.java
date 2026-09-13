package com.backhaulmatch.auth.controller;

import com.backhaulmatch.auth.dto.AdminUserDtos.AdminCreateUserRequest;
import com.backhaulmatch.auth.dto.AdminUserDtos.AdminUpdateUserRequest;
import com.backhaulmatch.auth.entity.User;
import com.backhaulmatch.auth.repository.UserRepository;
import com.backhaulmatch.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * User-management endpoints used by admin-service.
 *
 * IMPORTANT: these live at "/internal/admin" — deliberately NOT under
 * "/api/..." — so the Gateway's public `/api/auth/**` route can never reach
 * them. Only a service calling auth-service directly by its Eureka name
 * (admin-service) can hit this controller. Never expose these publicly.
 */
@RestController
@RequestMapping("/internal/admin/users")
@RequiredArgsConstructor
public class InternalAdminController {

    private final UserRepository userRepository;
    private final AuthService authService;

    // Never serializes the password — each user is mapped to a safe view.
    private static Map<String, Object> toView(User u) {
        return Map.of(
                "id", u.getId(),
                "username", u.getUsername(),
                "email", u.getEmail(),
                "role", u.getRole().name(),
                "enabled", u.isEnabled(),
                "approvalStatus", u.getApprovalStatus() == null ? "APPROVED" : u.getApprovalStatus().name(),
                "createdAt", u.getCreatedAt() == null ? null : u.getCreatedAt().toString()
        );
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list() {
        List<Map<String, Object>> users = userRepository.findAllByOrderByCreatedAtDesc()
                .stream().map(InternalAdminController::toView).toList();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> count() {
        return ResponseEntity.ok(Map.of("count", userRepository.count()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return ResponseEntity.ok(toView(user));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody AdminCreateUserRequest request) {
        User user = authService.registerUser(request);
        return ResponseEntity.ok(toView(user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id,
                                                      @Valid @RequestBody AdminUpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (request.username() != null && !request.username().isBlank()
                && !request.username().equals(user.getUsername())
                && userRepository.existsByUsername(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken");
        }
        if (request.email() != null && !request.email().isBlank()
                && !request.email().equalsIgnoreCase(user.getEmail())
                && userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        if (request.username() != null && !request.username().isBlank()) user.setUsername(request.username());
        if (request.email() != null && !request.email().isBlank()) user.setEmail(request.email());
        if (request.password() != null && !request.password().isBlank()) user.setPassword(request.password());
        if (request.role() != null && !request.role().isBlank()) {
            user.setRole(User.Role.valueOf(request.role().toUpperCase()));
        }
        return ResponseEntity.ok(toView(userRepository.save(user)));
    }

    @PatchMapping("/{id}/enabled")
    public ResponseEntity<Map<String, Object>> setEnabled(@PathVariable Long id,
                                                          @RequestParam boolean enabled) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setEnabled(enabled);
        return ResponseEntity.ok(toView(userRepository.save(user)));
    }

    @PatchMapping("/{id}/approval")
    public ResponseEntity<Map<String, Object>> setApproval(@PathVariable Long id,
                                                           @RequestParam User.ApprovalStatus status) {
        User user = authService.setApprovalStatus(id, status);
        return ResponseEntity.ok(toView(user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        userRepository.delete(user);
        return ResponseEntity.noContent().build();
    }
}
