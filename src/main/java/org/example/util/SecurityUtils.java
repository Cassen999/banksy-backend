package org.example.util;

import org.example.entity.User;
import org.example.repository.UserRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;

public class SecurityUtils {

    private SecurityUtils() {}

    public static User resolveUser(OAuth2User principal, UserRepository userRepository) {
        String email = principal.getAttribute("email");
        return userRepository.findByEmail(email).orElseThrow();
    }
}
