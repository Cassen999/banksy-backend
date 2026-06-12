package org.example.controller;

import org.example.model.MonthlyGlanceResponse;
import org.example.repository.UserRepository;
import org.example.service.MonthlyGlanceService;
import org.example.util.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MonthlyGlanceController {

    private final MonthlyGlanceService monthlyGlanceService;
    private final UserRepository userRepository;

    public MonthlyGlanceController(MonthlyGlanceService monthlyGlanceService, UserRepository userRepository) {
        this.monthlyGlanceService = monthlyGlanceService;
        this.userRepository = userRepository;
    }

    @GetMapping("/monthly-glance")
    public ResponseEntity<MonthlyGlanceResponse> getMonthlyGlance(
            @AuthenticationPrincipal OAuth2User principal) {
        try {
            return ResponseEntity.ok(monthlyGlanceService.getMonthlyGlance(
                    SecurityUtils.resolveUser(principal, userRepository).getId()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
