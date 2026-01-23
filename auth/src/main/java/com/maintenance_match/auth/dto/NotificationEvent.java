package com.maintenance_match.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {
    // Unique ID for idempotency (preventing duplicate DB inserts on retry)
    private UUID eventId;

    // The user receiving the notification
    private String recipientId;

    // Which channel this specific message is for
    private NotificationChannel channel;

    // --- For IN_APP Notifications ---
    private String message;

    // --- For EMAIL Notifications ---
    private String subject;
    private String template; // e.g., "welcome", "job-created"
    private Map<String, Object> variables; // Dynamic data for the template
}
