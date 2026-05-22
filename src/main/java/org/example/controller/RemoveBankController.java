package org.example.controller;

import org.example.repository.UserRepository;
import org.example.service.RemoveBankService;
import org.example.util.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/plaid")
public class RemoveBankController {

    private final RemoveBankService removeBankService;
    private final UserRepository userRepository;

    public RemoveBankController(RemoveBankService removeBankService, UserRepository userRepository) {
        this.removeBankService = removeBankService;
        this.userRepository = userRepository;
    }

    @PutMapping("/account/{plaidAccountId}/hide")
    public ResponseEntity<Map<String, String>> hideAccount(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable UUID plaidAccountId) {
        try {
            UUID userId = SecurityUtils.resolveUser(principal, userRepository).getId();
            removeBankService.hideAccount(plaidAccountId, userId);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/item/{plaidItemId}")
    public ResponseEntity<Map<String, String>> removeItem(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable UUID plaidItemId) {
        try {
            UUID userId = SecurityUtils.resolveUser(principal, userRepository).getId();
            removeBankService.removeItem(plaidItemId, userId);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
