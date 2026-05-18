package org.example.controller;

import org.example.entity.User;
import org.example.model.BalanceResponse;
import org.example.repository.UserRepository;
import org.example.service.BalanceService;
import org.example.service.CustomOAuth2UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BalanceController.class)
@ActiveProfiles("test")
class BalanceControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean BalanceService balanceService;
    @MockBean UserRepository userRepository;
    @MockBean CustomOAuth2UserService customOAuth2UserService;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
    }

    @Test
    void shouldReturnBalanceResponse_whenAuthenticatedUserCallsEndpoint() throws Exception {
        BalanceResponse.Account account = new BalanceResponse.Account(
                "Checking", "depository", "checking", 1500.0, 1400.0, "USD");
        when(balanceService.getBalance(any())).thenReturn(new BalanceResponse(List.of(account), List.of()));

        mockMvc.perform(get("/api/balance")
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts[0].name").value("Checking"))
                .andExpect(jsonPath("$.accounts[0].currentBalance").value(1500.0));
    }

    @Test
    void shouldReturn500_whenBalanceServiceThrowsException() throws Exception {
        when(balanceService.getBalance(any())).thenThrow(new RuntimeException("Plaid error"));

        mockMvc.perform(get("/api/balance")
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com"))))
                .andExpect(status().isInternalServerError());
    }
}
