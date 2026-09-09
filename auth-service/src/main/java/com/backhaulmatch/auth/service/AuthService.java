package com.backhaulmatch.auth.service;

import com.backhaulmatch.auth.client.AdminAuditClient;
import com.backhaulmatch.auth.dto.AdminAuditDtos.AdminAuditRequest;
import com.backhaulmatch.auth.dto.AdminUserDtos.AdminCreateUserRequest;
import com.backhaulmatch.auth.dto.AuthDtos.*;
import com.backhaulmatch.auth.entity.User;
import com.backhaulmatch.auth.repository.UserRepository;
import com.backhaulmatch.auth.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final AdminAuditClient adminAuditClient;

    public AuthResponse register(RegisterRequest req) {
        User user = registerUser(
                new AdminCreateUserRequest(req.username(), req.email(), req.password(), req.role()),
                User.ApprovalStatus.PENDING);

        adminAuditClient.send(new AdminAuditRequest(
                "USER_REGISTERED", user.getId(), user.getUsername(), "register",
                "Account created with role " + user.getRole() + " — awaiting admin approval"));

        // No token for a pending-approval account; the user must wait until an
        // admin approves it (login is blocked until then).
        return new AuthResponse(null, user.getId(), user.getUsername(), user.getRole());
    }

    /** Shared by the public register endpoint and admin-service's user creation. */
    public User registerUser(AdminCreateUserRequest req, User.ApprovalStatus approvalStatus) {
        if (userRepository.existsByUsername(req.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken");
        }
        if (userRepository.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        User user = new User();
        user.setUsername(req.username());
        user.setEmail(req.email());
        user.setPassword(req.password());
        user.setRole(User.Role.valueOf(req.role().toUpperCase()));
        user.setEnabled(true);
        user.setApprovalStatus(approvalStatus);
        return userRepository.save(user);
    }

    /** Users created by an administrator are approved immediately. */
    public User registerUser(AdminCreateUserRequest req) {
        return registerUser(req, User.ApprovalStatus.APPROVED);
    }

    /** Admin approval workflow — flips PENDING users to APPROVED/REJECTED. */
    public User setApprovalStatus(Long id, User.ApprovalStatus status) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setApprovalStatus(status);
        return userRepository.save(user);
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByUsername(req.username())
                .orElseThrow(() -> {
                    adminAuditClient.send(new AdminAuditRequest(
                            "FAILED_LOGIN", null, req.username(), "login", "Invalid username or password"));
                    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
                });

        if (!user.isEnabled()) {
            adminAuditClient.send(new AdminAuditRequest(
                    "FAILED_LOGIN", user.getId(), user.getUsername(), "login", "Account is disabled"));
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account is disabled by an administrator");
        }

        if (user.getApprovalStatus() == User.ApprovalStatus.PENDING) {
            adminAuditClient.send(new AdminAuditRequest(
                    "FAILED_LOGIN", user.getId(), user.getUsername(), "login", "Account pending admin approval"));
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account pending approval — ask an administrator to approve your registration");
        }

        if (user.getApprovalStatus() == User.ApprovalStatus.REJECTED) {
            adminAuditClient.send(new AdminAuditRequest(
                    "FAILED_LOGIN", user.getId(), user.getUsername(), "login", "Account registration was rejected"));
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your registration was rejected by an administrator");
        }

        if (!req.password().equals(user.getPassword())) {
            adminAuditClient.send(new AdminAuditRequest(
                    "FAILED_LOGIN", user.getId(), user.getUsername(), "login", "Invalid username or password"));
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        adminAuditClient.send(new AdminAuditRequest(
                "LOGIN", user.getId(), user.getUsername(), "login",
                user.getRole() + " signed in"));

        String token = jwtUtil.generateToken(user);
        return new AuthResponse(token, user.getId(), user.getUsername(), user.getRole());
    }
}
