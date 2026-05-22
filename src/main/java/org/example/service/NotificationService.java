package org.example.service;

import org.example.entity.Notification;
import org.example.repository.NotificationRepository;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyAll(List<UUID> userIds, String message) {
        notificationRepository.saveAll(userIds.stream().map(userId -> {
            Notification n = new Notification();
            n.setUser(userRepository.getReferenceById(userId));
            n.setMessage(message);
            return n;
        }).toList());
    }
}
