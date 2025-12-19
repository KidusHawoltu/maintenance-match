package com.maintenance_match.auth.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "notification-client", url = "${app.clients.notification-url}")
public interface NotificationClient {

    @PostMapping("/api/notifications/send")
    void sendNotification(@RequestBody NotificationRequest request);

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class NotificationRequest {
        private UUID userId;
        private UUID maintainerId;
        private String message;
    }
}
