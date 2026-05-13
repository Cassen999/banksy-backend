package org.example.config;

import com.plaid.client.request.PlaidApi;
import org.example.plaid.PlaidClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlaidApiConfig {

    @Bean
    public PlaidApi plaidApi() {
        return PlaidClientFactory.create();
    }
}
