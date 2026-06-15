package org.example.service;

import com.plaid.client.model.TransactionStream;
import com.plaid.client.model.TransactionsRecurringGetRequest;
import com.plaid.client.model.TransactionsRecurringGetResponse;
import org.example.entity.PlaidAccount;
import org.example.entity.PlaidItem;
import org.example.entity.PlaidItemStatus;
import org.example.entity.User;
import org.example.model.RecurringResponse;
import org.example.model.RelinkSignal;
import org.example.model.ScheduledDepositDto;
import org.example.plaid.PlaidClientFactory;
import org.example.plaid.PlaidTokenError;
import org.example.repository.PlaidAccountRepository;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import retrofit2.Response;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class RecurringService {

    private final PlaidEnvironmentService plaidEnvService;
    private final EncryptionService encryptionService;
    private final UserRepository userRepository;
    private final PlaidAccountRepository plaidAccountRepository;

    public RecurringService(PlaidEnvironmentService plaidEnvService,
                            EncryptionService encryptionService,
                            UserRepository userRepository,
                            PlaidAccountRepository plaidAccountRepository) {
        this.plaidEnvService = plaidEnvService;
        this.encryptionService = encryptionService;
        this.userRepository = userRepository;
        this.plaidAccountRepository = plaidAccountRepository;
    }

    @Transactional
    public RecurringResponse getRecurring(UUID userId, String accountId) throws IOException {
        if (accountId != null) {
            return getRecurringForAccount(userId, accountId);
        }
        return getAllRecurring(userId);
    }

    public List<ScheduledDepositDto> getScheduledDeposits(UUID userId) throws IOException {
        RecurringResponse response = getAllRecurring(userId);
        LocalDate today = LocalDate.now();
        LocalDate endOfMonth = YearMonth.now().atEndOfMonth();
        return response.inflowStreams().stream()
                .filter(s -> Boolean.TRUE.equals(s.isActive()))
                .filter(s -> s.predictedNextDate() != null)
                .filter(s -> !s.predictedNextDate().isBefore(today))
                .filter(s -> !s.predictedNextDate().isAfter(endOfMonth))
                .sorted(Comparator.comparing(RecurringResponse.TransactionStreamDto::predictedNextDate))
                .map(s -> new ScheduledDepositDto(
                        s.merchantName(),
                        s.description(),
                        s.frequency(),
                        s.firstDate(),
                        s.lastDate(),
                        s.predictedNextDate(),
                        s.averageAmount(),
                        s.lastAmount(),
                        s.isActive(),
                        s.personalFinanceCategory(),
                        s.status()))
                .toList();
    }

    RecurringResponse getAllRecurring(UUID userId) throws IOException {
        User user = userRepository.findByIdWithPlaidItems(userId).orElseThrow();
        List<RecurringResponse.TransactionStreamDto> allInflow = new ArrayList<>();
        List<RecurringResponse.TransactionStreamDto> allOutflow = new ArrayList<>();
        List<RelinkSignal> relinkRequired = new ArrayList<>();

        for (PlaidItem item : user.getPlaidItems()) {
            if (item.getStatus() != PlaidItemStatus.HEALTHY) {
                relinkRequired.add(RelinkSignal.from(item, userId,
                        RelinkSignal.errorTypeFromStatus(item.getStatus())));
                continue;
            }

            String accessToken = encryptionService.decrypt(item.getAccessTokenEnc());
            Response<TransactionsRecurringGetResponse> response = plaidEnvService.getClient()
                    .transactionsRecurringGet(new TransactionsRecurringGetRequest().accessToken(accessToken))
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
                throw new RuntimeException("Plaid recurring fetch failed: " + errorBody);
            }

            response.body().getInflowStreams().stream().map(this::toDto).forEach(allInflow::add);
            response.body().getOutflowStreams().stream().map(this::toDto).forEach(allOutflow::add);
        }

        return new RecurringResponse(allInflow, allOutflow, relinkRequired);
    }

    private RecurringResponse getRecurringForAccount(UUID userId, String accountId) throws IOException {
        PlaidAccount account = plaidAccountRepository.findByPlaidAccountIdWithItem(accountId)
                .orElseThrow(() -> new NoSuchElementException("Account not found: " + accountId));

        PlaidItem item = account.getPlaidItem();

        User user = userRepository.findByIdWithPlaidItems(userId).orElseThrow();
        boolean userLinked = user.getPlaidItems().stream()
                .anyMatch(pi -> pi.getId().equals(item.getId()));
        if (!userLinked) {
            throw new SecurityException("Not authorized to access this account");
        }

        List<RelinkSignal> relinkRequired = new ArrayList<>();
        if (item.getStatus() != PlaidItemStatus.HEALTHY) {
            relinkRequired.add(RelinkSignal.from(item, userId,
                    RelinkSignal.errorTypeFromStatus(item.getStatus())));
            return new RecurringResponse(List.of(), List.of(), relinkRequired);
        }

        String accessToken = encryptionService.decrypt(item.getAccessTokenEnc());
        Response<TransactionsRecurringGetResponse> response = plaidEnvService.getClient()
                .transactionsRecurringGet(new TransactionsRecurringGetRequest()
                        .accessToken(accessToken)
                        .accountIds(List.of(accountId)))
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
                return new RecurringResponse(List.of(), List.of(), relinkRequired);
            }
            throw new RuntimeException("Plaid recurring fetch failed: " + errorBody);
        }

        List<RecurringResponse.TransactionStreamDto> inflow = response.body().getInflowStreams()
                .stream().map(this::toDto).toList();
        List<RecurringResponse.TransactionStreamDto> outflow = response.body().getOutflowStreams()
                .stream().map(this::toDto).toList();
        return new RecurringResponse(inflow, outflow, relinkRequired);
    }

    private RecurringResponse.TransactionStreamDto toDto(TransactionStream s) {
        RecurringResponse.AmountDto averageAmount = s.getAverageAmount() != null
                ? new RecurringResponse.AmountDto(
                        s.getAverageAmount().getAmount(),
                        s.getAverageAmount().getIsoCurrencyCode())
                : null;
        RecurringResponse.AmountDto lastAmount = s.getLastAmount() != null
                ? new RecurringResponse.AmountDto(
                        s.getLastAmount().getAmount(),
                        s.getLastAmount().getIsoCurrencyCode())
                : null;
        RecurringResponse.PersonalFinanceCategoryDto pfc = s.getPersonalFinanceCategory() != null
                ? new RecurringResponse.PersonalFinanceCategoryDto(
                        s.getPersonalFinanceCategory().getPrimary(),
                        s.getPersonalFinanceCategory().getDetailed())
                : null;
        return new RecurringResponse.TransactionStreamDto(
                s.getAccountId(),
                s.getStreamId(),
                s.getMerchantName(),
                s.getDescription(),
                s.getFrequency() != null ? s.getFrequency().getValue() : null,
                s.getFirstDate(),
                s.getLastDate(),
                s.getPredictedNextDate(),
                averageAmount,
                lastAmount,
                s.getIsActive(),
                pfc,
                s.getStatus() != null ? s.getStatus().getValue() : null
        );
    }
}
