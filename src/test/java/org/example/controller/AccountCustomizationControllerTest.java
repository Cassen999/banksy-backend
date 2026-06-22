package org.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.entity.User;
import org.example.repository.UserRepository;
import org.example.service.AccountCustomizationService;
import org.example.service.CustomOAuth2UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountCustomizationController.class)
@ActiveProfiles("test")
class AccountCustomizationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean AccountCustomizationService accountCustomizationService;
    @MockBean UserRepository userRepository;
    @MockBean CustomOAuth2UserService customOAuth2UserService;

    private final String plaidAccountId = "acct-abc123";

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
    }

    // ─── PUT /api/plaid/account/{id}/name ────────────────────────────────────

    @Test
    void setCustomName_shouldReturn200_whenSuccessful() throws Exception {
        mockMvc.perform(put("/api/plaid/account/{id}/name", plaidAccountId)
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("customName", "My Checking"))))
                .andExpect(status().isOk());
    }

    @Test
    void setCustomName_shouldReturn404_whenAccountNotFound() throws Exception {
        doThrow(new NoSuchElementException("not found"))
                .when(accountCustomizationService).setCustomName(any(), eq(plaidAccountId), any());

        mockMvc.perform(put("/api/plaid/account/{id}/name", plaidAccountId)
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("customName", "My Checking"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void setCustomName_shouldReturn403_whenUserNotLinked() throws Exception {
        doThrow(new SecurityException("not authorized"))
                .when(accountCustomizationService).setCustomName(any(), eq(plaidAccountId), any());

        mockMvc.perform(put("/api/plaid/account/{id}/name", plaidAccountId)
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("customName", "My Checking"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void setCustomName_shouldRedirect_whenUnauthenticated() throws Exception {
        mockMvc.perform(put("/api/plaid/account/{id}/name", plaidAccountId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("customName", "My Checking"))))
                .andExpect(status().is3xxRedirection());
    }

    // ─── DELETE /api/plaid/account/{id}/name ─────────────────────────────────

    @Test
    void deleteCustomName_shouldReturn200_whenSuccessful() throws Exception {
        mockMvc.perform(delete("/api/plaid/account/{id}/name", plaidAccountId)
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com")))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void deleteCustomName_shouldReturn404_whenAccountNotFound() throws Exception {
        doThrow(new NoSuchElementException("not found"))
                .when(accountCustomizationService).deleteCustomName(any(), eq(plaidAccountId));

        mockMvc.perform(delete("/api/plaid/account/{id}/name", plaidAccountId)
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com")))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteCustomName_shouldReturn403_whenUserNotLinked() throws Exception {
        doThrow(new SecurityException("not authorized"))
                .when(accountCustomizationService).deleteCustomName(any(), eq(plaidAccountId));

        mockMvc.perform(delete("/api/plaid/account/{id}/name", plaidAccountId)
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com")))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteCustomName_shouldRedirect_whenUnauthenticated() throws Exception {
        mockMvc.perform(delete("/api/plaid/account/{id}/name", plaidAccountId)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
    }
}
