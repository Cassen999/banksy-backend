package org.example.controller;

import org.example.entity.User;
import org.example.model.MonthlyGlanceResponse;
import org.example.repository.UserRepository;
import org.example.service.CustomOAuth2UserService;
import org.example.service.MonthlyGlanceService;
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

@WebMvcTest(MonthlyGlanceController.class)
@ActiveProfiles("test")
class MonthlyGlanceControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean MonthlyGlanceService monthlyGlanceService;
    @MockBean UserRepository userRepository;
    @MockBean CustomOAuth2UserService customOAuth2UserService;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
    }

    @Test
    void shouldReturn200_withDailyTotals_whenServiceSucceeds() throws Exception {
        MonthlyGlanceResponse.DailyTotal entry = new MonthlyGlanceResponse.DailyTotal("2026-06-01", 45.20);
        when(monthlyGlanceService.getMonthlyGlance(any()))
                .thenReturn(new MonthlyGlanceResponse(List.of(entry), List.of()));

        mockMvc.perform(get("/api/monthly-glance")
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dailyTotals[0].transactionDate").value("2026-06-01"))
                .andExpect(jsonPath("$.dailyTotals[0].total").value(45.20));
    }

    @Test
    void shouldReturn500_whenServiceThrowsException() throws Exception {
        when(monthlyGlanceService.getMonthlyGlance(any())).thenThrow(new RuntimeException("Plaid error"));

        mockMvc.perform(get("/api/monthly-glance")
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com"))))
                .andExpect(status().isInternalServerError());
    }
}
