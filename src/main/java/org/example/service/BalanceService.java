package org.example.service;

import com.plaid.client.model.AccountBase;
import com.plaid.client.model.AccountsBalanceGetRequest;
import com.plaid.client.model.AccountsGetResponse;
import com.plaid.client.request.PlaidApi;
import org.example.config.PlaidConfig;
import org.example.model.BalanceResponse;
import org.example.plaid.PlaidClientFactory;
import org.springframework.stereotype.Service;
import retrofit2.Response;

import java.io.IOException;
import java.util.List;

@Service
public class BalanceService {

    private final PlaidApi plaidClient;

    public BalanceService() {
        this.plaidClient = PlaidClientFactory.create();
    }

    public BalanceResponse getBalance() throws IOException {
        AccountsBalanceGetRequest request = new AccountsBalanceGetRequest()
                .accessToken(PlaidConfig.getAccessToken());

        Response<AccountsGetResponse> response = plaidClient.accountsBalanceGet(request).execute();

        if (!response.isSuccessful() || response.body() == null) {
            String errorDetail = response.errorBody() != null ? response.errorBody().string() : "no details";
            throw new RuntimeException("Plaid balance fetch failed: " + errorDetail);
        }

        List<BalanceResponse.Account> accounts = response.body().getAccounts().stream()
                .map(this::toAccount)
                .toList();

        return new BalanceResponse(accounts);
    }

    private BalanceResponse.Account toAccount(AccountBase account) {
        return new BalanceResponse.Account(
                account.getName(),
                account.getType() != null ? account.getType().getValue() : null,
                account.getSubtype() != null ? account.getSubtype().getValue() : null,
                account.getBalances().getCurrent(),
                account.getBalances().getAvailable(),
                account.getBalances().getIsoCurrencyCode()
        );
    }
}
