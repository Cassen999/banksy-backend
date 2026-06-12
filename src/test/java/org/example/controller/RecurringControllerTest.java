package org.example.controller;

import org.example.entity.User;
import org.example.model.RecurringResponse;
import org.example.repository.UserRepository;
import org.example.service.CustomOAuth2UserService;
import org.example.service.RecurringService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RecurringController.class)
@ActiveProfiles("test")
class RecurringControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean RecurringService recurringService;
    @MockBean UserRepository userRepository;
    @MockBean CustomOAuth2UserService customOAuth2UserService;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
    }

    @Test
    void shouldReturn200WithStreams_whenGetAllAuthenticated() throws Exception {
        RecurringResponse response = new RecurringResponse(
                List.of(new RecurringResponse.TransactionStreamDto(
                        "acct-1", "stream-1", "Netflix", "Netflix", "MONTHLY",
                        null, null, null, null, null, true, null, "MATURE")),
                List.of(),
                List.of()
        );
        when(recurringService.getRecurring(any(), eq(null))).thenReturn(response);

        mockMvc.perform(get("/api/recurring")
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inflowStreams[0].merchantName").value("Netflix"))
                .andExpect(jsonPath("$.inflowStreams[0].frequency").value("MONTHLY"))
                .andExpect(jsonPath("$.outflowStreams").isEmpty())
                .andExpect(jsonPath("$.relinkRequired").isEmpty());
    }

    @Test
    void shouldReturn200WithStreams_whenPerAccountAuthenticated() throws Exception {
        RecurringResponse response = new RecurringResponse(
                List.of(),
                List.of(new RecurringResponse.TransactionStreamDto(
                        "acct-42", "stream-2", "Netflix", "Netflix", "MONTHLY",
                        null, null, null, null, null, true, null, "MATURE")),
                List.of()
        );
        when(recurringService.getRecurring(any(), eq("acct-42"))).thenReturn(response);

        mockMvc.perform(get("/api/recurring?accountId=acct-42")
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outflowStreams[0].accountId").value("acct-42"))
                .andExpect(jsonPath("$.inflowStreams").isEmpty());
    }

    @Test
    void shouldReturn404_whenServiceThrowsNoSuchElementException() throws Exception {
        when(recurringService.getRecurring(any(), any()))
                .thenThrow(new NoSuchElementException("Account not found"));

        mockMvc.perform(get("/api/recurring?accountId=bad-id")
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn403_whenServiceThrowsSecurityException() throws Exception {
        when(recurringService.getRecurring(any(), any()))
                .thenThrow(new SecurityException("Not authorized"));

        mockMvc.perform(get("/api/recurring?accountId=acct-123")
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn500_whenServiceThrowsGenericException() throws Exception {
        when(recurringService.getRecurring(any(), any()))
                .thenThrow(new RuntimeException("Plaid error"));

        mockMvc.perform(get("/api/recurring")
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com"))))
                .andExpect(status().isInternalServerError());
    }
}
