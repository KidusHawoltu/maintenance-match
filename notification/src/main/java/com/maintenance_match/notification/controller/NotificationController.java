package com.maintenance_match.notification.controller;

import com.maintenance_match.notification.dto.NotificationResponse;
import com.maintenance_match.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getMyNotifications(
             @Parameter(hidden = true) @RequestHeader("X-User-ID") String userIdHeader) {
        UUID userId = UUID.fromString(userIdHeader);
        return ResponseEntity.ok(notificationService.getMyNotifications(userId));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount(
            @Parameter(hidden = true) @RequestHeader("X-User-ID") String userIdHeader) {
        UUID userId = UUID.fromString(userIdHeader);
        return ResponseEntity.ok(notificationService.getUnreadCount(userId));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable UUID id,
            @Parameter(hidden = true) @RequestHeader("X-User-ID") String userIdHeader) {
        UUID userId = UUID.fromString(userIdHeader);
        notificationService.markAsRead(id, userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(
            @Parameter(hidden = true) @RequestHeader("X-User-ID") String userIdHeader) {
        UUID userId = UUID.fromString(userIdHeader);
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok().build();
    }
}