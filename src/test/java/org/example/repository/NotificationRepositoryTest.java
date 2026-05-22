package org.example.repository;

import org.example.entity.Notification;
import org.example.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRepositoryTest extends AbstractRepositoryTest {

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setFirstName("Test");
        user.setLastName("User");
        user.setUsername("notif-user-" + UUID.randomUUID());
        user.setEmail("notif-" + UUID.randomUUID() + "@example.com");
        user = userRepository.save(user);
    }

    @Test
    void shouldPersistNotification_withDefaultUnreadState() {
        Notification n = new Notification();
        n.setUser(user);
        n.setMessage("Test notification");
        Notification saved = notificationRepository.save(n);

        Notification found = notificationRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getMessage()).isEqualTo("Test notification");
        assertThat(found.isRead()).isFalse();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    void shouldSaveMultipleNotifications_forSameUser() {
        Notification n1 = new Notification();
        n1.setUser(user);
        n1.setMessage("First");

        Notification n2 = new Notification();
        n2.setUser(user);
        n2.setMessage("Second");

        notificationRepository.saveAll(List.of(n1, n2));

        List<Notification> all = notificationRepository.findAll();
        assertThat(all).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void shouldMarkNotificationAsRead() {
        Notification n = new Notification();
        n.setUser(user);
        n.setMessage("Unread");
        n = notificationRepository.save(n);

        n.setRead(true);
        notificationRepository.save(n);

        assertThat(notificationRepository.findById(n.getId()).orElseThrow().isRead()).isTrue();
    }
}
