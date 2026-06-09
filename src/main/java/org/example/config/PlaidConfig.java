package org.example.config;

import io.github.cdimascio.dotenv.Dotenv;

public class PlaidConfig {

    private static final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

    public static String getClientId() {
        return getRequired("PLAID_CLIENT_ID");
    }

    public static String getSecretSandbox() {
        return getRequired("PLAID_SECRET_SANDBOX");
    }

    public static String getSecretProduction() {
        return getRequired("PLAID_SECRET_PRODUCTION");
    }

    public static String getEnvironment() {
        return getRequired("PLAID_ENVIRONMENT");
    }

    private static String getRequired(String key) {
        String value = dotenv.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Missing required environment variable: " + key +
                ". Make sure it is set in your .env file."
            );
        }
        return value;
    }
}
