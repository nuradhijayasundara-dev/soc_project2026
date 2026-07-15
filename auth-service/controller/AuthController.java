package lk.backhaulmatch.auth.controller;

import jakarta.validation.Valid;
import lk.backhaulmatch.auth.dto.AuthResponse;
import lk.backhaulmatch.auth.dto.LoginRequest;
import lk.backhaulmatch.auth.dto.RegisterRequest;
import lk.backhaulmatch.auth.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
