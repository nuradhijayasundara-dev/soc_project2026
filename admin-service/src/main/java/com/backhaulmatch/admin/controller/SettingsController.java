package com.backhaulmatch.admin.controller;

import com.backhaulmatch.admin.dto.AdminDtos.SettingBatchUpdate;
import com.backhaulmatch.admin.entity.SystemSetting;
import com.backhaulmatch.admin.service.SettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.backhaulmatch.admin.controller.AdminGuard.requireAdmin;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    public ResponseEntity<List<SystemSetting>> list(@RequestHeader("X-User-Role") String role) {
        requireAdmin(role);
        return ResponseEntity.ok(settingsService.list());
    }

    @PutMapping
    public ResponseEntity<List<SystemSetting>> update(@RequestHeader("X-User-Role") String role,
                                                      @RequestHeader(value = "X-User-Username", required = false) String adminUsername,
                                                      @Valid @RequestBody SettingBatchUpdate batch) {
        requireAdmin(role);
        return ResponseEntity.ok(settingsService.update(batch.settings(), adminUsername));
    }

    // Read-only endpoint for other services (matching-service reads pricing +
    // matching preferences at runtime). Deliberately outside the admin guard.
    @GetMapping("/internal")
    public ResponseEntity<List<SystemSetting>> internalList() {
        return ResponseEntity.ok(settingsService.list());
    }
}
