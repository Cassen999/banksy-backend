package org.example.service;

import org.example.entity.Notification;
import org.example.entity.User;
import org.example.repository.NotificationRepository;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, userRepository);
    }

    @Test
    void shouldCreateOneNotificationPerUser() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        User user1 = mock(User.class);
        User user2 = mock(User.class);
        when(userRepository.getReferenceById(userId1)).thenReturn(user1);
        when(userRepository.getReferenceById(userId2)).thenReturn(user2);

        service.notifyAll(List.of(userId1, userId2), "Test message");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());
        List<Notification> saved = captor.getValue();

        assertThat(saved).hasSize(2);
        assertThat(saved).allMatch(n -> "Test message".equals(n.getMessage()));
        assertThat(saved.stream().map(Notification::getUser).toList())
                .containsExactlyInAnyOrder(user1, user2);
    }

    @Test
    void shouldDoNothing_whenUserListIsEmpty() {
        service.notifyAll(List.of(), "message");

        verify(notificationRepository).saveAll(List.of());
        verify(userRepository, never()).getReferenceById(any());
    }
}
