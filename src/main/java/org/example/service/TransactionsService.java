package org.example.service;

import com.plaid.client.model.TransactionsGetRequest;
import com.plaid.client.model.TransactionsGetResponse;
import com.plaid.client.request.PlaidApi;
import org.example.config.PlaidConfig;
import org.example.model.TransactionsResponse;
import org.example.plaid.PlaidClientFactory;
import org.springframework.stereotype.Service;
import retrofit2.Response;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@Service
public class TransactionsService {

    private final PlaidApi plaidClient;

    public TransactionsService() {
        this.plaidClient = PlaidClientFactory.create();
    }

    public TransactionsResponse getTransactions(int days) throws IOException {
        TransactionsGetRequest request = new TransactionsGetRequest()
                .accessToken(PlaidConfig.getAccessToken())
                .startDate(LocalDate.now().minusDays(days))
                .endDate(LocalDate.now());

        Response<TransactionsGetResponse> response = plaidClient.transactionsGet(request).execute();

        if (!response.isSuccessful() || response.body() == null) {
            String errorDetail = response.errorBody() != null ? response.errorBody().string() : "no details";
            throw new RuntimeException("Plaid transactions fetch failed: " + errorDetail);
        }

        List<TransactionsResponse.Transaction> transactions = response.body().getTransactions().stream()
                .map(t -> new TransactionsResponse.Transaction(
                        t.getDate(),
                        t.getName(),
                        t.getAmount(),
                        t.getIsoCurrencyCode(),
                        t.getCategory()
                ))
                .toList();

        return new TransactionsResponse(transactions, transactions.size());
    }
}
