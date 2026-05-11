package org.example.plaid;

import com.plaid.client.ApiClient;
import com.plaid.client.request.PlaidApi;
import org.example.config.PlaidConfig;

import java.util.HashMap;

public class PlaidClientFactory {

    private PlaidClientFactory() {}

    public static PlaidApi create() {
        HashMap<String, String> apiKeys = new HashMap<>();
        apiKeys.put("clientId", PlaidConfig.getClientId());
        apiKeys.put("secret", PlaidConfig.getSecret());

        ApiClient apiClient = new ApiClient(apiKeys);

        String environment = PlaidConfig.getEnvironment();
        switch (environment.toLowerCase()) {
            case "sandbox" -> apiClient.setPlaidAdapter(ApiClient.Sandbox);
            case "production" -> apiClient.setPlaidAdapter(ApiClient.Production);
            default -> throw new IllegalStateException(
                "Unknown PLAID_ENVIRONMENT: " + environment + ". Expected 'sandbox' or 'production'."
            );
        }

        return apiClient.createService(PlaidApi.class);
    }
}
