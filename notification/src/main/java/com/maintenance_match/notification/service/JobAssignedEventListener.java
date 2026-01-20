package com.maintenance_match.notification.service;

import com.maintenance_match.notification.dto.NotificationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobAssignedEventListener {

    private final NotificationService notificationService;

    @KafkaListener(topics = "${app.kafka.topics.notification-send}", groupId = "${spring.kafka.consumer.group-id}")
    public void onNotification(NotificationRequest request) {
        log.info("Received notification event for user {}", request.getUserId());
        notificationService.sendNotification(request);
    }
}