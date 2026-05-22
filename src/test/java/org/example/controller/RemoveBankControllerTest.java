package org.example.controller;

import org.example.entity.User;
import org.example.repository.UserRepository;
import org.example.service.CustomOAuth2UserService;
import org.example.service.RemoveBankService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RemoveBankController.class)
@ActiveProfiles("test")
class RemoveBankControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean RemoveBankService removeBankService;
    @MockBean UserRepository userRepository;
    @MockBean CustomOAuth2UserService customOAuth2UserService;

    private User user;
    private final UUID accountId = UUID.randomUUID();
    private final UUID itemId    = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
    }

    // ─── PUT /api/plaid/account/{id}/hide ────────────────────────────────────

    @Test
    void hideAccount_shouldReturn200_whenSuccessful() throws Exception {
        mockMvc.perform(put("/api/plaid/account/{id}/hide", accountId)
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com")))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void hideAccount_shouldReturn403_whenUserNotLinked() throws Exception {
        doThrow(new SecurityException("No access"))
                .when(removeBankService).hideAccount(any(), any());

        mockMvc.perform(put("/api/plaid/account/{id}/hide", accountId)
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com")))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void hideAccount_shouldReturn500_whenServiceThrows() throws Exception {
        doThrow(new RuntimeException("unexpected"))
                .when(removeBankService).hideAccount(any(), any());

        mockMvc.perform(put("/api/plaid/account/{id}/hide", accountId)
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com")))
                        .with(csrf()))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void hideAccount_shouldRedirect_whenUnauthenticated() throws Exception {
        // OAuth2 apps redirect unauthenticated requests to the login page (302), not 401
        mockMvc.perform(put("/api/plaid/account/{id}/hide", accountId)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
    }

    // ─── DELETE /api/plaid/item/{id} ─────────────────────────────────────────

    @Test
    void removeItem_shouldReturn200_whenSuccessful() throws Exception {
        mockMvc.perform(delete("/api/plaid/item/{id}", itemId)
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com")))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void removeItem_shouldReturn403_whenUserNotLinked() throws Exception {
        doThrow(new SecurityException("No access"))
                .when(removeBankService).removeItem(any(), any());

        mockMvc.perform(delete("/api/plaid/item/{id}", itemId)
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com")))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void removeItem_shouldReturn500_whenServiceThrows() throws Exception {
        doThrow(new RuntimeException("unexpected"))
                .when(removeBankService).removeItem(any(), any());

        mockMvc.perform(delete("/api/plaid/item/{id}", itemId)
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com")))
                        .with(csrf()))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void removeItem_shouldRedirect_whenUnauthenticated() throws Exception {
        // OAuth2 apps redirect unauthenticated requests to the login page (302), not 401
        mockMvc.perform(delete("/api/plaid/item/{id}", itemId)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
    }
}
