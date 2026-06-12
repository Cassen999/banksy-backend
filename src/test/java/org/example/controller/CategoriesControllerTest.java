package org.example.controller;

import org.example.entity.PlaidCategory;
import org.example.repository.PlaidCategoryRepository;
import org.example.repository.UserRepository;
import org.example.service.CustomOAuth2UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoriesController.class)
@ActiveProfiles("test")
class CategoriesControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean PlaidCategoryRepository categoryRepository;
    @MockBean UserRepository userRepository;
    @MockBean CustomOAuth2UserService customOAuth2UserService;

    @Test
    void shouldReturn200_withAllCategories() throws Exception {
        PlaidCategory primary = mock(PlaidCategory.class);
        when(primary.getCategory()).thenReturn("FOOD_AND_DRINK");
        when(primary.getCategoryType()).thenReturn("PRIMARY");
        when(primary.getPrimaryCategory()).thenReturn(null);

        PlaidCategory detailed = mock(PlaidCategory.class);
        when(detailed.getCategory()).thenReturn("FOOD_AND_DRINK_RESTAURANT");
        when(detailed.getCategoryType()).thenReturn("DETAILED");
        when(detailed.getPrimaryCategory()).thenReturn("FOOD_AND_DRINK");

        when(categoryRepository.findAll()).thenReturn(List.of(primary, detailed));

        mockMvc.perform(get("/api/categories")
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].category").value("FOOD_AND_DRINK"))
                .andExpect(jsonPath("$.categories[1].category").value("FOOD_AND_DRINK_RESTAURANT"))
                .andExpect(jsonPath("$.categories[1].primaryCategory").value("FOOD_AND_DRINK"));
    }

    @Test
    void shouldReturnBothPrimaryAndDetailedTypes() throws Exception {
        PlaidCategory primary = mock(PlaidCategory.class);
        when(primary.getCategory()).thenReturn("ENTERTAINMENT");
        when(primary.getCategoryType()).thenReturn("PRIMARY");
        when(primary.getPrimaryCategory()).thenReturn(null);

        PlaidCategory detailed = mock(PlaidCategory.class);
        when(detailed.getCategory()).thenReturn("ENTERTAINMENT_TV_AND_MOVIES");
        when(detailed.getCategoryType()).thenReturn("DETAILED");
        when(detailed.getPrimaryCategory()).thenReturn("ENTERTAINMENT");

        when(categoryRepository.findAll()).thenReturn(List.of(primary, detailed));

        mockMvc.perform(get("/api/categories")
                        .with(oidcLogin().userInfoToken(t -> t.claim("email", "test@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0].type").value("PRIMARY"))
                .andExpect(jsonPath("$.categories[1].type").value("DETAILED"));
    }
}
