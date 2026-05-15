package org.example.util;

import org.example.entity.User;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityUtilsTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void shouldReturnUser_whenEmailExistsInRepository() {
        OAuth2User principal = mock(OAuth2User.class);
        when(principal.getAttribute("email")).thenReturn("user@example.com");

        User user = new User();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        User result = SecurityUtils.resolveUser(principal, userRepository);

        assertThat(result).isEqualTo(user);
    }

    @Test
    void shouldThrowNoSuchElementException_whenUserNotFound() {
        OAuth2User principal = mock(OAuth2User.class);
        when(principal.getAttribute("email")).thenReturn("missing@example.com");
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> SecurityUtils.resolveUser(principal, userRepository))
                .isInstanceOf(NoSuchElementException.class);
    }
}
