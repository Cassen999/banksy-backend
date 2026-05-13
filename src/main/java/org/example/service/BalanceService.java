package org.example.service;

import com.plaid.client.model.AccountBase;
import com.plaid.client.model.AccountsBalanceGetRequest;
import com.plaid.client.model.AccountsGetResponse;
import com.plaid.client.request.PlaidApi;
import org.example.entity.PlaidItem;
import org.example.entity.User;
import org.example.model.BalanceResponse;
import org.example.plaid.PlaidClientFactory;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;
import retrofit2.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class BalanceService {

    private final PlaidApi plaidClient;
    private final EncryptionService encryptionService;
    private final UserRepository userRepository;

    public BalanceService(PlaidApi plaidClient,
                          EncryptionService encryptionService,
                          UserRepository userRepository) {
        this.plaidClient = plaidClient;
        this.encryptionService = encryptionService;
        this.userRepository = userRepository;
    }

    public BalanceResponse getBalance(UUID userId) throws IOException {
        User user = userRepository.findByIdWithPlaidItems(userId).orElseThrow();
        List<BalanceResponse.Account> allAccounts = new ArrayList<>();

        for (PlaidItem item : user.getPlaidItems()) {
            String accessToken = encryptionService.decrypt(item.getAccessTokenEnc());
            Response<AccountsGetResponse> response = plaidClient
                    .accountsBalanceGet(new AccountsBalanceGetRequest().accessToken(accessToken))
                    .execute();

            if (!response.isSuccessful() || response.body() == null) {
                throw new RuntimeException("Plaid balance fetch failed: " + PlaidClientFactory.extractErrorDetail(response));
            }

            response.body().getAccounts().stream().map(this::toAccount).forEach(allAccounts::add);
        }

        return new BalanceResponse(allAccounts);
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
