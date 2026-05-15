package org.example.controller;

import org.example.entity.User;
import org.example.model.TransactionsResponse;
import org.example.repository.UserRepository;
import org.example.service.CustomOAuth2UserService;
import org.example.service.TransactionsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionsController.class)
@ActiveProfiles("test")
class TransactionsControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean TransactionsService transactionsService;
    @MockBean UserRepository userRepository;
    @MockBean CustomOAuth2UserService customOAuth2UserService;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
    }

    @Test
    void shouldReturnTransactionsResponse_whenAuthenticatedUserCallsEndpoint() throws Exception {
        TransactionsResponse.Transaction tx = new TransactionsResponse.Transaction(
                LocalDate.now(), "Groceries", 42.50, "USD", List.of("Food"));
        when(transactionsService.getTransactions(any(), eq(30)))
                .thenReturn(new TransactionsResponse(List.of(tx), 1));

        mockMvc.perform(get("/api/transactions")
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions[0].name").value("Groceries"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void shouldUseDefaultDays_whenNoQueryParamProvided() throws Exception {
        when(transactionsService.getTransactions(any(), eq(30)))
                .thenReturn(new TransactionsResponse(List.of(), 0));

        mockMvc.perform(get("/api/transactions")
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com"))))
                .andExpect(status().isOk());

        verify(transactionsService).getTransactions(any(), eq(30));
    }

    @Test
    void shouldReturn500_whenTransactionsServiceThrowsException() throws Exception {
        when(transactionsService.getTransactions(any(), anyInt())).thenThrow(new RuntimeException("Plaid error"));

        mockMvc.perform(get("/api/transactions")
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com"))))
                .andExpect(status().isInternalServerError());
    }
}
