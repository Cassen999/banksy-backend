package org.example.controller;

import org.example.entity.User;
import org.example.repository.UserRepository;
import org.example.service.CustomOAuth2UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean UserRepository userRepository;
    @MockBean CustomOAuth2UserService customOAuth2UserService;

    @Test
    void shouldReturn200_whenLogoutCalled() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .with(csrf())
                        .with(oidcLogin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loggedOut").value(true));
    }

    @Test
    void shouldReturnUserInfo_whenAuthenticatedUserCallsMe() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(user.getEmail()).thenReturn("test@example.com");
        when(user.getFirstName()).thenReturn("Jane");
        when(user.getLastName()).thenReturn("Doe");
        when(user.getUsername()).thenReturn("jane");

        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/auth/me")
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.username").value("jane"));
    }

    @Test
    void shouldReturn500_whenUserNotFoundOnMeEndpoint() throws Exception {
        when(userRepository.findByEmail(any())).thenThrow(new NoSuchElementException());

        mockMvc.perform(get("/api/auth/me")
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "ghost@example.com"))))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void shouldRedirectToLogin_whenUnauthenticatedUserCallsMe() throws Exception {
        // OAuth2 apps redirect unauthenticated requests to the login page (302), not 401
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().is3xxRedirection());
    }
}
