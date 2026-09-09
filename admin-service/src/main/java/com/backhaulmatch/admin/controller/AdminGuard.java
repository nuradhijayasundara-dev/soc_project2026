package com.backhaulmatch.admin.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Checks the "X-User-Role" header the Gateway's JwtAuthFilter already forwards. */
public final class AdminGuard {

    private AdminGuard() {}

    public static void requireAdmin(String role) {
        if (role == null || !"ADMIN".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }
}
