package org.example.controller;

import org.example.model.TransactionsResponse;
import org.example.repository.UserRepository;
import org.example.service.TransactionsService;
import org.example.util.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TransactionsController {

    private final TransactionsService transactionsService;
    private final UserRepository userRepository;

    public TransactionsController(TransactionsService transactionsService, UserRepository userRepository) {
        this.transactionsService = transactionsService;
        this.userRepository = userRepository;
    }

    @GetMapping("/transactions")
    public ResponseEntity<TransactionsResponse> getTransactions(
            @AuthenticationPrincipal OAuth2User principal,
            @RequestParam(defaultValue = "30") int days) {
        try {
            return ResponseEntity.ok(transactionsService.getTransactions(
                    SecurityUtils.resolveUser(principal, userRepository).getId(), days));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
