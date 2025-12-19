package com.maintenance_match.notification.repository;

import com.maintenance_match.notification.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    // Find all unread notifications for a specific user
    List<Notification> findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(UUID recipientId);

    // Find all notifications for a user (for a history view)
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId);
}
