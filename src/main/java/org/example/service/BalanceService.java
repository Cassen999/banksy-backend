package org.example.service;

import com.plaid.client.model.AccountBase;
import com.plaid.client.model.AccountsBalanceGetRequest;
import com.plaid.client.model.AccountsGetResponse;
import org.example.entity.PlaidItem;
import org.example.entity.PlaidItemStatus;
import org.example.entity.User;
import org.example.model.BalanceResponse;
import org.example.model.RelinkSignal;
import org.example.plaid.PlaidClientFactory;
import org.example.plaid.PlaidTokenError;
import org.example.repository.PlaidAccountRepository;
import org.example.repository.UserAccountNameRepository;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import retrofit2.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BalanceService {

    private final PlaidEnvironmentService plaidEnvService;
    private final EncryptionService encryptionService;
    private final UserRepository userRepository;
    private final PlaidAccountRepository plaidAccountRepository;
    private final UserAccountNameRepository userAccountNameRepository;

    public BalanceService(PlaidEnvironmentService plaidEnvService,
                          EncryptionService encryptionService,
                          UserRepository userRepository,
                          PlaidAccountRepository plaidAccountRepository,
                          UserAccountNameRepository userAccountNameRepository) {
        this.plaidEnvService = plaidEnvService;
        this.encryptionService = encryptionService;
        this.userRepository = userRepository;
        this.plaidAccountRepository = plaidAccountRepository;
        this.userAccountNameRepository = userAccountNameRepository;
    }

    @Transactional
    public BalanceResponse getBalance(UUID userId) throws IOException {
        User user = userRepository.findByIdWithPlaidItems(userId).orElseThrow();
        Map<String, String> nameMap = userAccountNameRepository.findAllByUserId(userId).stream()
                .collect(Collectors.toMap(n -> n.getPlaidAccountId(), n -> n.getCustomName()));
        List<BalanceResponse.Account> allAccounts = new ArrayList<>();
        List<RelinkSignal> relinkRequired = new ArrayList<>();

        for (PlaidItem item : user.getPlaidItems()) {
            if (item.getStatus() != PlaidItemStatus.HEALTHY) {
                relinkRequired.add(RelinkSignal.from(item, userId,
                        RelinkSignal.errorTypeFromStatus(item.getStatus())));
                continue;
            }

            String accessToken = encryptionService.decrypt(item.getAccessTokenEnc());
            Response<AccountsGetResponse> response = plaidEnvService.getClient()
                    .accountsBalanceGet(new AccountsBalanceGetRequest().accessToken(accessToken))
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
                throw new RuntimeException("Plaid balance fetch failed: " + errorBody);
            }

            Set<String> hiddenIds = plaidAccountRepository.findHiddenAccountIdsByItemId(item.getId());
            response.body().getAccounts().stream()
                    .filter(a -> a.getAccountId() == null || !hiddenIds.contains(a.getAccountId()))
                    .map(a -> toAccount(a, item.getInstitutionName(), nameMap.get(a.getAccountId())))
                    .forEach(allAccounts::add);
        }

        return new BalanceResponse(allAccounts, relinkRequired);
    }

    private BalanceResponse.Account toAccount(AccountBase account, String institutionName, String customName) {
        return new BalanceResponse.Account(
                account.getAccountId(),
                account.getName(),
                account.getType() != null ? account.getType().getValue() : null,
                account.getSubtype() != null ? account.getSubtype().getValue() : null,
                account.getBalances().getCurrent(),
                account.getBalances().getAvailable(),
                account.getBalances().getIsoCurrencyCode(),
                institutionName,
                customName
        );
    }
}
