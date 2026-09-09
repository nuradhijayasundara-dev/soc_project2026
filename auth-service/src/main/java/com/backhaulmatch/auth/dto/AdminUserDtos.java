package com.backhaulmatch.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class AdminUserDtos {

    // Create: every field required. Role: ADMIN | COURIER_OPERATOR | FLEET_MANAGER | DRIVER
    public record AdminCreateUserRequest(
            @NotBlank String username,
            @NotBlank @Email String email,
            @NotBlank String password,
            @NotBlank String role
    ) {}

    // Update: every field optional. Blank password = keep the existing one.
    public record AdminUpdateUserRequest(
            String username,
            @Email String email,
            String password,
            String role
    ) {}
}
