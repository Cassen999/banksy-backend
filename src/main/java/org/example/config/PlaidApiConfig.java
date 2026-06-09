package org.example.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class PlaidApiConfig {
    // PlaidApi is no longer a singleton bean; obtain it via PlaidEnvironmentService.getClient()
}
