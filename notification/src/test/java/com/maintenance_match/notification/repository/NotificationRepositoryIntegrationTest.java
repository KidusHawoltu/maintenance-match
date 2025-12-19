package com.maintenance_match.notification.repository;

import com.maintenance_match.notification.AbstractIntegrationTest;
import com.maintenance_match.notification.model.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class NotificationRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private NotificationRepository notificationRepository;

    private UUID testRecipientId1;
    private UUID testRecipientId2;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();

        testRecipientId1 = UUID.randomUUID();
        testRecipientId2 = UUID.randomUUID();

        // Create a mix of notifications
        Notification n1_user1_unread = Notification.builder().recipientId(testRecipientId1).message("Message 1").isRead(false).build();
        Notification n2_user1_read = Notification.builder().recipientId(testRecipientId1).message("Message 2").isRead(true).build();
        Notification n3_user2_unread = Notification.builder().recipientId(testRecipientId2).message("Message 3").isRead(false).build();

        // Save out of order to test the sorting
        notificationRepository.saveAll(List.of(n2_user1_read, n3_user2_unread, n1_user1_unread));
    }

    @Test
    void findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc_shouldReturnOnlyUnread() {
        // When
        List<Notification> unreadNotifications = notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(testRecipientId1);

        // Then
        assertThat(unreadNotifications).hasSize(1);
        assertThat(unreadNotifications.get(0).getMessage()).isEqualTo("Message 1");
        assertThat(unreadNotifications.get(0).isRead()).isFalse();
    }

    @Test
    void findByRecipientIdOrderByCreatedAtDesc_shouldReturnAllForUser() {
        // When
        List<Notification> allNotifications = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(testRecipientId1);

        // Then
        assertThat(allNotifications).hasSize(2);
        assertThat(allNotifications).extracting(Notification::getMessage).containsExactly("Message 1", "Message 2");
    }
}
