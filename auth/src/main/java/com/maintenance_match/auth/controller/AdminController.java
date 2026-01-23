package com.maintenance_match.auth.controller;

import com.maintenance_match.auth.dto.AdminUserView;
import com.maintenance_match.auth.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    public ResponseEntity<List<AdminUserView>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @GetMapping("/maintainers/pending")
    public ResponseEntity<List<AdminUserView>> getPendingMaintainers() {
        return ResponseEntity.ok(adminService.getPendingMaintainers());
    }

    @PostMapping("/maintainers/{userId}/approve")
    public ResponseEntity<AdminUserView> approveMaintainer(
            @PathVariable UUID userId) {
        return ResponseEntity.ok(adminService.approveMaintainer(userId));
    }

    @PostMapping("/maintainers/{userId}/reject")
    public ResponseEntity<AdminUserView> rejectMaintainer(
            @PathVariable UUID userId) {
        return ResponseEntity.ok(adminService.rejectMaintainer(userId));
    }

    @PutMapping("/users/{userId}/disable")
    public ResponseEntity<AdminUserView> disableUser(
            @PathVariable UUID userId) {
        return ResponseEntity.ok(adminService.disableUser(userId));
    }
}