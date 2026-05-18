package org.example.plaid;

import com.plaid.client.ApiClient;
import com.plaid.client.request.PlaidApi;
import org.example.config.PlaidConfig;
import retrofit2.Response;

import java.io.IOException;
import java.util.HashMap;
import java.util.Optional;

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

    public static String extractErrorDetail(Response<?> response) throws IOException {
        return response.errorBody() != null ? response.errorBody().string() : "no details";
    }

    public static Optional<PlaidTokenError> classifyTokenError(String errorBody) {
        if (errorBody == null) return Optional.empty();
        if (errorBody.contains("ITEM_LOGIN_REQUIRED")) return Optional.of(PlaidTokenError.LOGIN_REQUIRED);
        if (errorBody.contains("INVALID_ACCESS_TOKEN")) return Optional.of(PlaidTokenError.INVALID_TOKEN);
        return Optional.empty();
    }
}
