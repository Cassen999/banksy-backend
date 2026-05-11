package org.example.controller;

import org.example.model.BalanceResponse;
import org.example.service.BalanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BalanceController {

    private final BalanceService balanceService;

    public BalanceController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @GetMapping("/balance")
    public ResponseEntity<BalanceResponse> getBalance() {
        try {
            return ResponseEntity.ok(balanceService.getBalance());
        } catch (Exception e) {
            // Return 500 without exposing internal error details to the client
            return ResponseEntity.internalServerError().build();
        }
    }
}
