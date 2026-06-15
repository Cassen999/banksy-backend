package org.example.controller;

import org.example.model.RecurringResponse;
import org.example.model.ScheduledDepositDto;
import org.example.repository.UserRepository;
import org.example.service.RecurringService;
import org.example.util.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class RecurringController {

    private final RecurringService recurringService;
    private final UserRepository userRepository;

    public RecurringController(RecurringService recurringService, UserRepository userRepository) {
        this.recurringService = recurringService;
        this.userRepository = userRepository;
    }

    @GetMapping("/recurring/scheduled-deposits")
    public ResponseEntity<?> getScheduledDeposits(@AuthenticationPrincipal OAuth2User principal) {
        try {
            UUID userId = SecurityUtils.resolveUser(principal, userRepository).getId();
            return ResponseEntity.ok(recurringService.getScheduledDeposits(userId));
        } catch (Exception e) {
            return ResponseEntity.status(403).body("Error getting scheduled deposit data");
        }
    }

    @GetMapping("/recurring")
    public ResponseEntity<RecurringResponse> getRecurring(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestParam(required = false) String accountId) {
        try {
            UUID userId = SecurityUtils.resolveUser(principal, userRepository).getId();
            return ResponseEntity.ok(recurringService.getRecurring(userId, accountId));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
