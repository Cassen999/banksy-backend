package org.example.controller;

import org.example.model.TransactionsResponse;
import org.example.service.TransactionsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TransactionsController {

    private final TransactionsService transactionsService;

    public TransactionsController(TransactionsService transactionsService) {
        this.transactionsService = transactionsService;
    }

    @GetMapping("/transactions")
    public ResponseEntity<TransactionsResponse> getTransactions(
            @RequestParam(defaultValue = "30") int days) {
        try {
            return ResponseEntity.ok(transactionsService.getTransactions(days));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
