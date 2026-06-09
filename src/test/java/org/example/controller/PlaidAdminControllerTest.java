package org.example.controller;

import org.example.service.CustomOAuth2UserService;
import org.example.service.PlaidEnvironmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlaidAdminController.class)
@ActiveProfiles("test")
class PlaidAdminControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean PlaidEnvironmentService plaidEnvironmentService;
    @MockBean CustomOAuth2UserService customOAuth2UserService;

    @Test
    void shouldReturnCurrentEnvironment() throws Exception {
        when(plaidEnvironmentService.getCurrentEnvironment()).thenReturn("sandbox");

        mockMvc.perform(get("/api/dev/plaid/environment")
                        .with(oidcLogin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environment").value("sandbox"));
    }

    @Test
    void shouldReturnNewEnvironmentAfterToggle() throws Exception {
        when(plaidEnvironmentService.toggle()).thenReturn("production");

        mockMvc.perform(post("/api/dev/plaid/environment/toggle")
                        .with(oidcLogin())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environment").value("production"));

        verify(plaidEnvironmentService).toggle();
    }

    @Test
    void shouldRedirectToOAuth_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/dev/plaid/environment"))
                .andExpect(status().is3xxRedirection());
    }
}
