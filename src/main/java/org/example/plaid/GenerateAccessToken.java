package org.example.plaid;

import com.plaid.client.request.PlaidApi;
import com.plaid.client.model.ItemPublicTokenExchangeRequest;
import com.plaid.client.model.ItemPublicTokenExchangeResponse;
import com.plaid.client.model.Products;
import com.plaid.client.model.SandboxPublicTokenCreateRequest;
import com.plaid.client.model.SandboxPublicTokenCreateResponse;
import retrofit2.Response;

import java.io.IOException;
import java.util.List;

/**
 * Run once to generate a sandbox access token. Copy the printed token into .env as PLAID_ACCESS_TOKEN.
 * Delete or do not run this in production.
 */
public class GenerateAccessToken {

    public static void main(String[] args) throws IOException {
        PlaidApi plaid = PlaidClientFactory.create();

        // Step 1: create a sandbox public token for First Platypus Bank
        SandboxPublicTokenCreateRequest createRequest = new SandboxPublicTokenCreateRequest()
                .institutionId("ins_109508")
                .initialProducts(List.of(Products.TRANSACTIONS, Products.AUTH));

        Response<SandboxPublicTokenCreateResponse> createResponse =
                plaid.sandboxPublicTokenCreate(createRequest).execute();

        if (!createResponse.isSuccessful() || createResponse.body() == null) {
            System.err.println("Failed to create public token: " +
                    (createResponse.errorBody() != null ? createResponse.errorBody().string() : "unknown error"));
            return;
        }

        String publicToken = createResponse.body().getPublicToken();

        // Step 2: exchange the public token for a permanent access token
        ItemPublicTokenExchangeRequest exchangeRequest = new ItemPublicTokenExchangeRequest()
                .publicToken(publicToken);

        Response<ItemPublicTokenExchangeResponse> exchangeResponse =
                plaid.itemPublicTokenExchange(exchangeRequest).execute();

        if (!exchangeResponse.isSuccessful() || exchangeResponse.body() == null) {
            System.err.println("Failed to exchange token: " +
                    (exchangeResponse.errorBody() != null ? exchangeResponse.errorBody().string() : "unknown error"));
            return;
        }

        String accessToken = exchangeResponse.body().getAccessToken();
        System.out.println("\nAdd this to your .env file:");
        System.out.println("PLAID_ACCESS_TOKEN=" + accessToken);
    }
}
