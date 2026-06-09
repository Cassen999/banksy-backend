package org.example.controller;

import org.example.service.PlaidEnvironmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dev/plaid")
public class PlaidAdminController {

    private final PlaidEnvironmentService plaidEnvironmentService;

    public PlaidAdminController(PlaidEnvironmentService plaidEnvironmentService) {
        this.plaidEnvironmentService = plaidEnvironmentService;
    }

    @GetMapping("/environment")
    public ResponseEntity<Map<String, String>> getEnvironment() {
        return ResponseEntity.ok(Map.of("environment", plaidEnvironmentService.getCurrentEnvironment()));
    }

    @PostMapping("/environment/toggle")
    public ResponseEntity<Map<String, String>> toggleEnvironment() {
        String newEnv = plaidEnvironmentService.toggle();
        return ResponseEntity.ok(Map.of("environment", newEnv));
    }
}
