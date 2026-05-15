package org.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.entity.User;
import org.example.repository.UserRepository;
import org.example.service.CustomOAuth2UserService;
import org.example.service.PlaidLinkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlaidLinkController.class)
@ActiveProfiles("test")
class PlaidLinkControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean PlaidLinkService plaidLinkService;
    @MockBean UserRepository userRepository;
    @MockBean CustomOAuth2UserService customOAuth2UserService;

    private User user;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
    }

    @Test
    void shouldReturnLinkToken_whenAuthenticatedUserRequestsIt() throws Exception {
        when(plaidLinkService.createLinkToken(any())).thenReturn("link-token-abc");

        mockMvc.perform(get("/api/plaid/link-token")
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.link_token").value("link-token-abc"));
    }

    @Test
    void shouldReturn500_whenLinkTokenServiceThrowsException() throws Exception {
        when(plaidLinkService.createLinkToken(any())).thenThrow(new RuntimeException("Plaid error"));

        mockMvc.perform(get("/api/plaid/link-token")
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com"))))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void shouldReturn200_whenPublicTokenExchangedSuccessfully() throws Exception {
        Map<String, String> body = Map.of(
                "publicToken", "public-token",
                "institutionId", "ins-1",
                "institutionName", "Test Bank");

        mockMvc.perform(post("/api/plaid/exchange")
                        .with(csrf())
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void shouldReturn500_whenExchangeServiceThrowsException() throws Exception {
        when(plaidLinkService.exchangeAndStore(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("exchange failed"));

        Map<String, String> body = Map.of(
                "publicToken", "bad-token",
                "institutionId", "ins-1",
                "institutionName", "Bank");

        mockMvc.perform(post("/api/plaid/exchange")
                        .with(csrf())
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void shouldReturn200_whenShareSuccessful() throws Exception {
        Map<String, Object> body = Map.of(
                "plaidItemId", UUID.randomUUID().toString(),
                "shareWithEmail", "friend@example.com");

        mockMvc.perform(post("/api/plaid/share")
                        .with(csrf())
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void shouldReturn400_whenShareThrowsIllegalArgument() throws Exception {
        doThrow(new IllegalArgumentException("Bank connection not found"))
                .when(plaidLinkService).shareItem(any(), any(), any());

        Map<String, Object> body = Map.of(
                "plaidItemId", UUID.randomUUID().toString(),
                "shareWithEmail", "nobody@example.com");

        mockMvc.perform(post("/api/plaid/share")
                        .with(csrf())
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bank connection not found"));
    }

    @Test
    void shouldReturn403_whenShareThrowsSecurityException() throws Exception {
        doThrow(new SecurityException("You do not have access"))
                .when(plaidLinkService).shareItem(any(), any(), any());

        Map<String, Object> body = Map.of(
                "plaidItemId", UUID.randomUUID().toString(),
                "shareWithEmail", "attacker@example.com");

        mockMvc.perform(post("/api/plaid/share")
                        .with(csrf())
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("You do not have access"));
    }

    @Test
    void shouldReturn500_whenShareThrowsGenericException() throws Exception {
        doThrow(new RuntimeException("unexpected"))
                .when(plaidLinkService).shareItem(any(), any(), any());

        Map<String, Object> body = Map.of(
                "plaidItemId", UUID.randomUUID().toString(),
                "shareWithEmail", "x@example.com");

        mockMvc.perform(post("/api/plaid/share")
                        .with(csrf())
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isInternalServerError());
    }
}
