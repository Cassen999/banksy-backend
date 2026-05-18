package org.example.service;

import com.plaid.client.model.TransactionsGetRequest;
import com.plaid.client.model.TransactionsGetResponse;
import com.plaid.client.request.PlaidApi;
import org.example.entity.PlaidItem;
import org.example.entity.PlaidItemStatus;
import org.example.entity.User;
import org.example.model.RelinkSignal;
import org.example.model.TransactionsResponse;
import org.example.plaid.PlaidClientFactory;
import org.example.plaid.PlaidTokenError;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import retrofit2.Response;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransactionsService {

    private final PlaidApi plaidClient;
    private final EncryptionService encryptionService;
    private final UserRepository userRepository;

    public TransactionsService(PlaidApi plaidClient,
                               EncryptionService encryptionService,
                               UserRepository userRepository) {
        this.plaidClient = plaidClient;
        this.encryptionService = encryptionService;
        this.userRepository = userRepository;
    }

    @Transactional
    public TransactionsResponse getTransactions(UUID userId, int days) throws IOException {
        User user = userRepository.findByIdWithPlaidItems(userId).orElseThrow();
        List<TransactionsResponse.Transaction> allTransactions = new ArrayList<>();
        List<RelinkSignal> relinkRequired = new ArrayList<>();

        for (PlaidItem item : user.getPlaidItems()) {
            if (item.getStatus() != PlaidItemStatus.HEALTHY) {
                relinkRequired.add(RelinkSignal.from(item, userId,
                        RelinkSignal.errorTypeFromStatus(item.getStatus())));
                continue;
            }

            String accessToken = encryptionService.decrypt(item.getAccessTokenEnc());
            Response<TransactionsGetResponse> response = plaidClient
                    .transactionsGet(new TransactionsGetRequest()
                            .accessToken(accessToken)
                            .startDate(LocalDate.now().minusDays(days))
                            .endDate(LocalDate.now()))
                    .execute();

            if (!response.isSuccessful() || response.body() == null) {
                String errorBody = PlaidClientFactory.extractErrorDetail(response);
                Optional<PlaidTokenError> tokenError = PlaidClientFactory.classifyTokenError(errorBody);
                if (tokenError.isPresent()) {
                    PlaidItemStatus newStatus = tokenError.get() == PlaidTokenError.LOGIN_REQUIRED
                            ? PlaidItemStatus.NEEDS_REAUTH
                            : PlaidItemStatus.INVALID_TOKEN;
                    item.setStatus(newStatus);
                    relinkRequired.add(RelinkSignal.from(item, userId, tokenError.get()));
                    continue;
                }
                throw new RuntimeException("Plaid transactions fetch failed: " + errorBody);
            }

            response.body().getTransactions().stream()
                    .map(t -> new TransactionsResponse.Transaction(
                            t.getDate(), t.getName(), t.getAmount(),
                            t.getIsoCurrencyCode(), t.getCategory()))
                    .forEach(allTransactions::add);
        }

        return new TransactionsResponse(allTransactions, allTransactions.size(), relinkRequired);
    }
}
