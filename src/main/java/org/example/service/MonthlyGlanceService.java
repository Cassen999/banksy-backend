package org.example.service;

import com.plaid.client.model.TransactionsGetRequest;
import com.plaid.client.model.TransactionsGetResponse;
import org.example.entity.PlaidItem;
import org.example.entity.PlaidItemStatus;
import org.example.entity.User;
import org.example.model.MonthlyGlanceResponse;
import org.example.model.RelinkSignal;
import org.example.plaid.PlaidClientFactory;
import org.example.plaid.PlaidTokenError;
import org.example.repository.PlaidAccountRepository;
import org.example.repository.UserExcludedAccountRepository;
import org.example.repository.UserRejectedCategoryRepository;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import retrofit2.Response;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class MonthlyGlanceService {

    private static final Set<String> DEFAULT_REJECTED = Set.of(
            "RENT_AND_UTILITIES",
            "INCOME",
            "TRANSFER_IN",
            "LOAN_DISBURSEMENTS"
    );

    private final PlaidEnvironmentService plaidEnvService;
    private final EncryptionService encryptionService;
    private final UserRepository userRepository;
    private final PlaidAccountRepository plaidAccountRepository;
    private final UserRejectedCategoryRepository rejectedCategoryRepository;
    private final UserExcludedAccountRepository excludedAccountRepository;

    public MonthlyGlanceService(PlaidEnvironmentService plaidEnvService,
                                 EncryptionService encryptionService,
                                 UserRepository userRepository,
                                 PlaidAccountRepository plaidAccountRepository,
                                 UserRejectedCategoryRepository rejectedCategoryRepository,
                                 UserExcludedAccountRepository excludedAccountRepository) {
        this.plaidEnvService = plaidEnvService;
        this.encryptionService = encryptionService;
        this.userRepository = userRepository;
        this.plaidAccountRepository = plaidAccountRepository;
        this.rejectedCategoryRepository = rejectedCategoryRepository;
        this.excludedAccountRepository = excludedAccountRepository;
    }

    @Transactional
    public MonthlyGlanceResponse getMonthlyGlance(UUID userId) throws IOException {
        User user = userRepository.findByIdWithPlaidItems(userId).orElseThrow();

        Set<String> rejectedCategories = new HashSet<>(DEFAULT_REJECTED);
        rejectedCategories.addAll(rejectedCategoryRepository.findCategoriesByUserId(userId));
        Set<String> excludedAccountIds = excludedAccountRepository.findPlaidAccountIdsByUserId(userId);

        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate today = LocalDate.now();
        Map<LocalDate, Double> dailyTotals = new HashMap<>();
        List<RelinkSignal> relinkRequired = new ArrayList<>();

        for (PlaidItem item : user.getPlaidItems()) {
            if (item.getStatus() != PlaidItemStatus.HEALTHY) {
                relinkRequired.add(RelinkSignal.from(item, userId,
                        RelinkSignal.errorTypeFromStatus(item.getStatus())));
                continue;
            }

            String accessToken = encryptionService.decrypt(item.getAccessTokenEnc());
            Response<TransactionsGetResponse> response = plaidEnvService.getClient()
                    .transactionsGet(new TransactionsGetRequest()
                            .accessToken(accessToken)
                            .startDate(monthStart)
                            .endDate(today))
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

            Set<String> hiddenIds = plaidAccountRepository.findHiddenAccountIdsByItemId(item.getId());

            response.body().getTransactions().stream()
                    .filter(t -> {
                        String acctId = t.getAccountId();
                        return (acctId == null || !hiddenIds.contains(acctId))
                                && (acctId == null || !excludedAccountIds.contains(acctId));
                    })
                    .filter(t -> {
                        var pfc = t.getPersonalFinanceCategory();
                        if (pfc == null) return true;
                        return !rejectedCategories.contains(pfc.getPrimary())
                                && !rejectedCategories.contains(pfc.getDetailed());
                    })
                    .forEach(t -> dailyTotals.merge(t.getDate(), t.getAmount(), Double::sum));
        }

        List<MonthlyGlanceResponse.DailyTotal> result = new ArrayList<>();
        LocalDate d = monthStart;
        while (!d.isAfter(today)) {
            result.add(new MonthlyGlanceResponse.DailyTotal(d.toString(), dailyTotals.getOrDefault(d, 0.0)));
            d = d.plusDays(1);
        }

        return new MonthlyGlanceResponse(result, relinkRequired);
    }
}
