package org.example.controller;

import org.example.repository.UserRepository;
import org.example.service.AccountCustomizationService;
import org.example.util.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/plaid")
public class AccountCustomizationController {

    private final AccountCustomizationService accountCustomizationService;
    private final UserRepository userRepository;

    public AccountCustomizationController(AccountCustomizationService accountCustomizationService,
                                          UserRepository userRepository) {
        this.accountCustomizationService = accountCustomizationService;
        this.userRepository = userRepository;
    }

    record SetNameRequest(String customName) {}

    @PutMapping("/account/{plaidAccountId}/name")
    public ResponseEntity<Map<String, String>> setCustomName(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable String plaidAccountId,
            @RequestBody SetNameRequest request) {
        try {
            UUID userId = SecurityUtils.resolveUser(principal, userRepository).getId();
            accountCustomizationService.setCustomName(userId, plaidAccountId, request.customName());
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/account/{plaidAccountId}/name")
    public ResponseEntity<Map<String, String>> deleteCustomName(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable String plaidAccountId) {
        try {
            UUID userId = SecurityUtils.resolveUser(principal, userRepository).getId();
            accountCustomizationService.deleteCustomName(userId, plaidAccountId);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
