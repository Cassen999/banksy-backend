package org.example.service;

import org.example.entity.OAuthIdentity;
import org.example.entity.User;
import org.example.repository.OAuthIdentityRepository;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private OAuthIdentityRepository oAuthIdentityRepository;

    private CustomOAuth2UserService service;
    private OidcUser mockOidcUser;
    private OidcUserRequest mockRequest;

    @BeforeEach
    void setUp() {
        service = spy(new CustomOAuth2UserService(userRepository, oAuthIdentityRepository));

        mockOidcUser = mock(OidcUser.class);
        mockRequest = mock(OidcUserRequest.class);
        ClientRegistration registration = mock(ClientRegistration.class);

        when(mockRequest.getClientRegistration()).thenReturn(registration);
        when(registration.getRegistrationId()).thenReturn("google");
        when(mockOidcUser.getSubject()).thenReturn("google-sub-123");
        when(mockOidcUser.getEmail()).thenReturn("test@example.com");
        when(mockOidcUser.getGivenName()).thenReturn("Jane");
        when(mockOidcUser.getFamilyName()).thenReturn("Doe");

        doReturn(mockOidcUser).when(service).loadFromOidcProvider(any());
    }

    @Test
    void shouldReturnOidcUser_whenOAuthIdentityAlreadyExists() {
        when(oAuthIdentityRepository.findByProviderAndProviderUserId("google", "google-sub-123"))
                .thenReturn(Optional.of(new OAuthIdentity()));

        OidcUser result = service.loadUser(mockRequest);

        assertThat(result).isEqualTo(mockOidcUser);
        verify(userRepository, never()).save(any());
        verify(oAuthIdentityRepository, never()).save(any());
    }

    @Test
    void shouldCreateUserAndIdentity_whenNeitherExists() {
        when(oAuthIdentityRepository.findByProviderAndProviderUserId("google", "google-sub-123"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenReturn(new User());
        when(oAuthIdentityRepository.save(any())).thenReturn(new OAuthIdentity());

        service.loadUser(mockRequest);

        verify(userRepository).save(any(User.class));
        verify(oAuthIdentityRepository).save(any(OAuthIdentity.class));
    }

    @Test
    void shouldCreateIdentityOnly_whenEmailAlreadyExists() {
        User existingUser = new User();
        when(oAuthIdentityRepository.findByProviderAndProviderUserId("google", "google-sub-123"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(existingUser));
        when(oAuthIdentityRepository.save(any())).thenReturn(new OAuthIdentity());

        service.loadUser(mockRequest);

        verify(userRepository, never()).save(any());
        verify(oAuthIdentityRepository).save(any(OAuthIdentity.class));
    }

    @Test
    void shouldDefaultFirstNameToEmpty_whenGivenNameIsNull() {
        when(mockOidcUser.getGivenName()).thenReturn(null);
        when(oAuthIdentityRepository.findByProviderAndProviderUserId(any(), any()))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(oAuthIdentityRepository.save(any())).thenReturn(new OAuthIdentity());

        service.loadUser(mockRequest);

        verify(userRepository).save(argThat(u -> ((User) u).getFirstName().isEmpty()));
    }

    @Test
    void shouldDefaultLastNameToEmpty_whenFamilyNameIsNull() {
        when(mockOidcUser.getFamilyName()).thenReturn(null);
        when(oAuthIdentityRepository.findByProviderAndProviderUserId(any(), any()))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(oAuthIdentityRepository.save(any())).thenReturn(new OAuthIdentity());

        service.loadUser(mockRequest);

        verify(userRepository).save(argThat(u -> ((User) u).getLastName().isEmpty()));
    }

    @Test
    void shouldDeriveUsername_whenEmailProvided() {
        when(oAuthIdentityRepository.findByProviderAndProviderUserId(any(), any()))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(oAuthIdentityRepository.save(any())).thenReturn(new OAuthIdentity());

        service.loadUser(mockRequest);

        verify(userRepository).save(argThat(u -> "test".equals(((User) u).getUsername())));
    }
}
