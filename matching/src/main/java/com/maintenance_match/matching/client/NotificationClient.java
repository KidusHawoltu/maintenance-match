package com.maintenance_match.matching.client;

import com.maintenance_match.matching.dto.NotificationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "notification-client",
        url = "${app.clients.notification-url}"
)
public interface NotificationClient {

    @PostMapping("/api/notifications/send")
    void sendNotification(@RequestBody NotificationRequest notificationRequest);

}
