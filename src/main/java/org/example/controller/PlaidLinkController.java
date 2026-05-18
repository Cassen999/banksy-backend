package org.example.controller;

import org.example.model.RelinkSignal;
import org.example.repository.UserRepository;
import org.example.service.PlaidLinkService;
import org.example.util.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/plaid")
public class PlaidLinkController {

    private final PlaidLinkService plaidLinkService;
    private final UserRepository userRepository;

    public PlaidLinkController(PlaidLinkService plaidLinkService, UserRepository userRepository) {
        this.plaidLinkService = plaidLinkService;
        this.userRepository = userRepository;
    }

    @GetMapping("/link-token")
    public ResponseEntity<Map<String, String>> getLinkToken(@AuthenticationPrincipal OAuth2User principal) {
        try {
            UUID userId = SecurityUtils.resolveUser(principal, userRepository).getId();
            return ResponseEntity.ok(Map.of("link_token", plaidLinkService.createLinkToken(userId)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/link-token/refresh/{itemId}")
    public ResponseEntity<Map<String, String>> refreshLinkToken(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable UUID itemId) {
        try {
            UUID userId = SecurityUtils.resolveUser(principal, userRepository).getId();
            return ResponseEntity.ok(Map.of("link_token", plaidLinkService.linkTokenRefresh(userId, itemId)));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/link-token/full-relink/{itemId}")
    public ResponseEntity<Map<String, String>> fullRelinkLinkToken(
            @AuthenticationPrincipal OAuth2User principal,
            @PathVariable UUID itemId) {
        try {
            UUID userId = SecurityUtils.resolveUser(principal, userRepository).getId();
            return ResponseEntity.ok(Map.of("link_token", plaidLinkService.fullRelinkToken(userId, itemId)));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, List<RelinkSignal>>> getStatus(@AuthenticationPrincipal OAuth2User principal) {
        try {
            UUID userId = SecurityUtils.resolveUser(principal, userRepository).getId();
            return ResponseEntity.ok(Map.of("relinkRequired", plaidLinkService.getRelinkStatus(userId)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/exchange")
    public ResponseEntity<Map<String, String>> exchange(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestBody ExchangeRequest body) {
        try {
            UUID userId = SecurityUtils.resolveUser(principal, userRepository).getId();
            UUID newItemId = plaidLinkService.exchangeAndStore(
                    body.publicToken(), body.institutionId(), body.institutionName(), userId);
            if (body.expiredItemId() != null) {
                plaidLinkService.replaceExpiredItem(body.expiredItemId(), newItemId);
            }
            return ResponseEntity.ok(Map.of("status", "ok", "message", "Plaid authentication successful"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/share")
    public ResponseEntity<Map<String, String>> share(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestBody ShareRequest body) {
        try {
            UUID userId = SecurityUtils.resolveUser(principal, userRepository).getId();
            plaidLinkService.shareItem(body.plaidItemId(), userId, body.shareWithEmail());
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    public record ExchangeRequest(String publicToken, String institutionId, String institutionName, UUID expiredItemId) {}
    public record ShareRequest(UUID plaidItemId, String shareWithEmail) {}
}
