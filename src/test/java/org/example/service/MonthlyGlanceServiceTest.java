package org.example.service;

import com.plaid.client.model.PersonalFinanceCategory;
import com.plaid.client.model.Transaction;
import com.plaid.client.model.TransactionsGetResponse;
import com.plaid.client.request.PlaidApi;
import okhttp3.ResponseBody;
import org.example.entity.PlaidItem;
import org.example.entity.PlaidItemStatus;
import org.example.entity.User;
import org.example.model.MonthlyGlanceResponse;
import org.example.plaid.PlaidTokenError;
import org.example.repository.PlaidAccountRepository;
import org.example.repository.UserExcludedAccountRepository;
import org.example.repository.UserRejectedCategoryRepository;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MonthlyGlanceServiceTest {

    @Mock private PlaidEnvironmentService plaidEnvService;
    @Mock private PlaidApi plaidClient;
    @Mock private EncryptionService encryptionService;
    @Mock private UserRepository userRepository;
    @Mock private PlaidAccountRepository plaidAccountRepository;
    @Mock private UserRejectedCategoryRepository rejectedCategoryRepository;
    @Mock private UserExcludedAccountRepository excludedAccountRepository;

    private MonthlyGlanceService service;
    private final UUID userId = UUID.randomUUID();
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        lenient().when(plaidEnvService.getClient()).thenReturn(plaidClient);
        lenient().when(rejectedCategoryRepository.findCategoriesByUserId(userId)).thenReturn(Set.of());
        lenient().when(excludedAccountRepository.findPlaidAccountIdsByUserId(userId)).thenReturn(Set.of());
        service = new MonthlyGlanceService(
                plaidEnvService, encryptionService, userRepository,
                plaidAccountRepository, rejectedCategoryRepository, excludedAccountRepository);
    }

    // --- category filtering ---

    @Test
    @SuppressWarnings("unchecked")
    void shouldExcludeTransactions_inDefaultRejectedPrimaryCategory() throws IOException {
        PlaidItem item = healthyItem("enc");
        when(encryptionService.decrypt("enc")).thenReturn("token");
        when(plaidAccountRepository.findHiddenAccountIdsByItemId(any())).thenReturn(Set.of());
        User user = userWithItems(item);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));
        Call<TransactionsGetResponse> call = buildCall(List.of(
                transaction("acct-1", today, 120.0, "RENT_AND_UTILITIES", "RENT_AND_UTILITIES_RENT")));
        when(plaidClient.transactionsGet(any())).thenReturn(call);

        MonthlyGlanceResponse result = service.getMonthlyGlance(userId);

        assertThat(dailyTotalFor(result, today)).isEqualTo(0.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExcludeTransactions_inDefaultRejectedPrimaryCategory_income() throws IOException {
        PlaidItem item = healthyItem("enc");
        when(encryptionService.decrypt("enc")).thenReturn("token");
        when(plaidAccountRepository.findHiddenAccountIdsByItemId(any())).thenReturn(Set.of());
        User user = userWithItems(item);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));
        Call<TransactionsGetResponse> call = buildCall(List.of(
                transaction("acct-1", today, -5000.0, "INCOME", "INCOME_SALARY")));
        when(plaidClient.transactionsGet(any())).thenReturn(call);

        MonthlyGlanceResponse result = service.getMonthlyGlance(userId);

        assertThat(dailyTotalFor(result, today)).isEqualTo(0.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldIncludeTransactions_withNullPersonalFinanceCategory() throws IOException {
        PlaidItem item = healthyItem("enc");
        when(encryptionService.decrypt("enc")).thenReturn("token");
        when(plaidAccountRepository.findHiddenAccountIdsByItemId(any())).thenReturn(Set.of());
        User user = userWithItems(item);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));
        Call<TransactionsGetResponse> call = buildCall(List.of(
                transaction("acct-1", today, 50.0, null, null)));
        when(plaidClient.transactionsGet(any())).thenReturn(call);

        MonthlyGlanceResponse result = service.getMonthlyGlance(userId);

        assertThat(dailyTotalFor(result, today)).isEqualTo(50.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExcludeTransactions_whenUserAddedDetailedCategoryMatches() throws IOException {
        when(rejectedCategoryRepository.findCategoriesByUserId(userId))
                .thenReturn(Set.of("PERSONAL_CARE_GYMS_AND_FITNESS_CENTERS"));
        PlaidItem item = healthyItem("enc");
        when(encryptionService.decrypt("enc")).thenReturn("token");
        when(plaidAccountRepository.findHiddenAccountIdsByItemId(any())).thenReturn(Set.of());
        User user = userWithItems(item);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));
        Call<TransactionsGetResponse> call = buildCall(List.of(
                transaction("acct-1", today, 50.0, "PERSONAL_CARE", "PERSONAL_CARE_GYMS_AND_FITNESS_CENTERS")));
        when(plaidClient.transactionsGet(any())).thenReturn(call);

        MonthlyGlanceResponse result = service.getMonthlyGlance(userId);

        assertThat(dailyTotalFor(result, today)).isEqualTo(0.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldIncludeTransactions_whenCategoryNotInRejectedSet() throws IOException {
        PlaidItem item = healthyItem("enc");
        when(encryptionService.decrypt("enc")).thenReturn("token");
        when(plaidAccountRepository.findHiddenAccountIdsByItemId(any())).thenReturn(Set.of());
        User user = userWithItems(item);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));
        Call<TransactionsGetResponse> call = buildCall(List.of(
                transaction("acct-1", today, 45.0, "FOOD_AND_DRINK", "FOOD_AND_DRINK_RESTAURANT")));
        when(plaidClient.transactionsGet(any())).thenReturn(call);

        MonthlyGlanceResponse result = service.getMonthlyGlance(userId);

        assertThat(dailyTotalFor(result, today)).isEqualTo(45.0);
    }

    // --- account filtering ---

    @Test
    @SuppressWarnings("unchecked")
    void shouldExcludeTransactions_fromHiddenAccounts() throws IOException {
        UUID itemId = UUID.randomUUID();
        PlaidItem item = healthyItem("enc");
        when(item.getId()).thenReturn(itemId);
        when(encryptionService.decrypt("enc")).thenReturn("token");
        when(plaidAccountRepository.findHiddenAccountIdsByItemId(itemId)).thenReturn(Set.of("acct-hidden"));
        User user = userWithItems(item);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));
        Call<TransactionsGetResponse> call = buildCall(List.of(
                transaction("acct-hidden", today, 50.0, "FOOD_AND_DRINK", "FOOD_AND_DRINK_RESTAURANT")));
        when(plaidClient.transactionsGet(any())).thenReturn(call);

        MonthlyGlanceResponse result = service.getMonthlyGlance(userId);

        assertThat(dailyTotalFor(result, today)).isEqualTo(0.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExcludeTransactions_fromUserExcludedAccounts() throws IOException {
        when(excludedAccountRepository.findPlaidAccountIdsByUserId(userId)).thenReturn(Set.of("acct-excluded"));
        PlaidItem item = healthyItem("enc");
        when(encryptionService.decrypt("enc")).thenReturn("token");
        when(plaidAccountRepository.findHiddenAccountIdsByItemId(any())).thenReturn(Set.of());
        User user = userWithItems(item);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));
        Call<TransactionsGetResponse> call = buildCall(List.of(
                transaction("acct-excluded", today, 50.0, "FOOD_AND_DRINK", "FOOD_AND_DRINK_RESTAURANT")));
        when(plaidClient.transactionsGet(any())).thenReturn(call);

        MonthlyGlanceResponse result = service.getMonthlyGlance(userId);

        assertThat(dailyTotalFor(result, today)).isEqualTo(0.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldIncludeTransactions_fromNonExcludedAccounts() throws IOException {
        PlaidItem item = healthyItem("enc");
        when(encryptionService.decrypt("enc")).thenReturn("token");
        when(plaidAccountRepository.findHiddenAccountIdsByItemId(any())).thenReturn(Set.of());
        User user = userWithItems(item);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));
        Call<TransactionsGetResponse> call = buildCall(List.of(
                transaction("acct-normal", today, 30.0, "FOOD_AND_DRINK", "FOOD_AND_DRINK_GROCERIES")));
        when(plaidClient.transactionsGet(any())).thenReturn(call);

        MonthlyGlanceResponse result = service.getMonthlyGlance(userId);

        assertThat(dailyTotalFor(result, today)).isEqualTo(30.0);
    }

    // --- aggregation ---

    @Test
    @SuppressWarnings("unchecked")
    void shouldAggregateAmountsByDate() throws IOException {
        PlaidItem item = healthyItem("enc");
        when(encryptionService.decrypt("enc")).thenReturn("token");
        when(plaidAccountRepository.findHiddenAccountIdsByItemId(any())).thenReturn(Set.of());
        User user = userWithItems(item);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));
        Call<TransactionsGetResponse> call = buildCall(List.of(
                transaction("acct-1", today, 20.0, "FOOD_AND_DRINK", "FOOD_AND_DRINK_RESTAURANT"),
                transaction("acct-1", today, 15.0, "FOOD_AND_DRINK", "FOOD_AND_DRINK_COFFEE")));
        when(plaidClient.transactionsGet(any())).thenReturn(call);

        MonthlyGlanceResponse result = service.getMonthlyGlance(userId);

        assertThat(dailyTotalFor(result, today)).isEqualTo(35.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNetRefundsAgainstSpending() throws IOException {
        PlaidItem item = healthyItem("enc");
        when(encryptionService.decrypt("enc")).thenReturn("token");
        when(plaidAccountRepository.findHiddenAccountIdsByItemId(any())).thenReturn(Set.of());
        User user = userWithItems(item);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));
        Call<TransactionsGetResponse> call = buildCall(List.of(
                transaction("acct-1", today,  50.0, "FOOD_AND_DRINK", "FOOD_AND_DRINK_RESTAURANT"),
                transaction("acct-1", today, -10.0, "FOOD_AND_DRINK", "FOOD_AND_DRINK_RESTAURANT")));
        when(plaidClient.transactionsGet(any())).thenReturn(call);

        MonthlyGlanceResponse result = service.getMonthlyGlance(userId);

        assertThat(dailyTotalFor(result, today)).isEqualTo(40.0);
    }

    @Test
    void shouldReturnEmptyDailyTotals_whenUserHasNoItems() throws IOException {
        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(new ArrayList<>());
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        MonthlyGlanceResponse result = service.getMonthlyGlance(userId);

        assertThat(result.dailyTotals()).hasSize(today.getDayOfMonth());
        assertThat(result.dailyTotals()).allMatch(dt -> dt.total() == 0.0);
        assertThat(result.relinkRequired()).isEmpty();
        verifyNoInteractions(plaidClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldIncludeZeroTotalDays_forDaysWithNoQualifyingTransactions() throws IOException {
        PlaidItem item = healthyItem("enc");
        when(encryptionService.decrypt("enc")).thenReturn("token");
        when(plaidAccountRepository.findHiddenAccountIdsByItemId(any())).thenReturn(Set.of());
        User user = userWithItems(item);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));
        Call<TransactionsGetResponse> call = buildCall(List.of(
                transaction("acct-1", today.withDayOfMonth(1), 40.0, "FOOD_AND_DRINK", "FOOD_AND_DRINK_GROCERIES")));
        when(plaidClient.transactionsGet(any())).thenReturn(call);

        MonthlyGlanceResponse result = service.getMonthlyGlance(userId);

        long zeroDays = result.dailyTotals().stream().filter(dt -> dt.total() == 0.0).count();
        assertThat(zeroDays).isEqualTo(today.getDayOfMonth() - 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnAllDaysFromFirstOfMonthThroughToday() throws IOException {
        PlaidItem item = healthyItem("enc");
        when(encryptionService.decrypt("enc")).thenReturn("token");
        when(plaidAccountRepository.findHiddenAccountIdsByItemId(any())).thenReturn(Set.of());
        User user = userWithItems(item);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));
        Call<TransactionsGetResponse> call = buildCall(List.of());
        when(plaidClient.transactionsGet(any())).thenReturn(call);

        MonthlyGlanceResponse result = service.getMonthlyGlance(userId);

        assertThat(result.dailyTotals()).hasSize(today.getDayOfMonth());
        assertThat(result.dailyTotals().get(0).transactionDate())
                .isEqualTo(today.withDayOfMonth(1).toString());
        assertThat(result.dailyTotals().get(result.dailyTotals().size() - 1).transactionDate())
                .isEqualTo(today.toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAggregateAcrossMultipleItems() throws IOException {
        PlaidItem item1 = healthyItem("enc-1");
        PlaidItem item2 = healthyItem("enc-2");
        when(encryptionService.decrypt("enc-1")).thenReturn("token-1");
        when(encryptionService.decrypt("enc-2")).thenReturn("token-2");
        when(plaidAccountRepository.findHiddenAccountIdsByItemId(any())).thenReturn(Set.of());
        User user = userWithItems(item1, item2);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));
        Call<TransactionsGetResponse> call1 = buildCall(List.of(
                transaction("acct-1", today, 30.0, "FOOD_AND_DRINK", "FOOD_AND_DRINK_RESTAURANT")));
        Call<TransactionsGetResponse> call2 = buildCall(List.of(
                transaction("acct-2", today, 25.0, "ENTERTAINMENT", "ENTERTAINMENT_TV_AND_MOVIES")));
        when(plaidClient.transactionsGet(any())).thenReturn(call1).thenReturn(call2);

        MonthlyGlanceResponse result = service.getMonthlyGlance(userId);

        assertThat(dailyTotalFor(result, today)).isEqualTo(55.0);
    }

    // --- relink / error handling ---

    @Test
    void shouldSkipItemAndAddRelinkSignal_whenStatusIsNeedsReauth() throws IOException {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(userId);
        PlaidItem item = mock(PlaidItem.class);
        when(item.getStatus()).thenReturn(PlaidItemStatus.NEEDS_REAUTH);
        when(item.getOwner()).thenReturn(owner);
        when(item.getId()).thenReturn(UUID.randomUUID());
        when(item.getInstitutionName()).thenReturn("Chase");
        User user = userWithItems(item);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        MonthlyGlanceResponse result = service.getMonthlyGlance(userId);

        assertThat(result.relinkRequired()).hasSize(1);
        assertThat(result.relinkRequired().get(0).errorType()).isEqualTo(PlaidTokenError.LOGIN_REQUIRED);
        verifyNoInteractions(plaidClient);
    }

    @Test
    void shouldSkipItemAndAddRelinkSignal_whenStatusIsInvalidToken() throws IOException {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(userId);
        PlaidItem item = mock(PlaidItem.class);
        when(item.getStatus()).thenReturn(PlaidItemStatus.INVALID_TOKEN);
        when(item.getOwner()).thenReturn(owner);
        when(item.getId()).thenReturn(UUID.randomUUID());
        when(item.getInstitutionName()).thenReturn("Wells Fargo");
        User user = userWithItems(item);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        MonthlyGlanceResponse result = service.getMonthlyGlance(userId);

        assertThat(result.relinkRequired()).hasSize(1);
        assertThat(result.relinkRequired().get(0).errorType()).isEqualTo(PlaidTokenError.INVALID_TOKEN);
        verifyNoInteractions(plaidClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldUpdateStatusAndAddRelinkSignal_whenPlaidReturnsItemLoginRequired() throws IOException {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(userId);
        PlaidItem item = healthyItem("enc");
        when(item.getOwner()).thenReturn(owner);
        when(item.getInstitutionName()).thenReturn("Chase");
        when(encryptionService.decrypt("enc")).thenReturn("token");
        User user = userWithItems(item);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));
        Call<TransactionsGetResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.error(400,
                ResponseBody.create(null, "{\"error_code\":\"ITEM_LOGIN_REQUIRED\"}")));
        when(plaidClient.transactionsGet(any())).thenReturn(call);

        MonthlyGlanceResponse result = service.getMonthlyGlance(userId);

        verify(item).setStatus(PlaidItemStatus.NEEDS_REAUTH);
        assertThat(result.relinkRequired()).hasSize(1);
        assertThat(result.relinkRequired().get(0).errorType()).isEqualTo(PlaidTokenError.LOGIN_REQUIRED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldUpdateStatusAndAddRelinkSignal_whenPlaidReturnsInvalidAccessToken() throws IOException {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(userId);
        PlaidItem item = healthyItem("enc");
        when(item.getOwner()).thenReturn(owner);
        when(item.getInstitutionName()).thenReturn("Chase");
        when(encryptionService.decrypt("enc")).thenReturn("token");
        User user = userWithItems(item);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));
        Call<TransactionsGetResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.error(400,
                ResponseBody.create(null, "{\"error_code\":\"INVALID_ACCESS_TOKEN\"}")));
        when(plaidClient.transactionsGet(any())).thenReturn(call);

        MonthlyGlanceResponse result = service.getMonthlyGlance(userId);

        verify(item).setStatus(PlaidItemStatus.INVALID_TOKEN);
        assertThat(result.relinkRequired()).hasSize(1);
        assertThat(result.relinkRequired().get(0).errorType()).isEqualTo(PlaidTokenError.INVALID_TOKEN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldThrowRuntimeException_whenPlaidReturnsNonTokenError() throws IOException {
        PlaidItem item = healthyItem("enc");
        when(encryptionService.decrypt("enc")).thenReturn("token");
        User user = userWithItems(item);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));
        Call<TransactionsGetResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.error(500, ResponseBody.create(null, "fail")));
        when(plaidClient.transactionsGet(any())).thenReturn(call);

        assertThatThrownBy(() -> service.getMonthlyGlance(userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Plaid transactions fetch failed");
    }

    // --- helpers ---

    private PlaidItem healthyItem(String encToken) {
        PlaidItem item = mock(PlaidItem.class);
        when(item.getStatus()).thenReturn(PlaidItemStatus.HEALTHY);
        when(item.getAccessTokenEnc()).thenReturn(encToken);
        lenient().when(item.getId()).thenReturn(UUID.randomUUID());
        return item;
    }

    @SuppressWarnings("unchecked")
    private Call<TransactionsGetResponse> buildCall(List<Transaction> transactions) throws IOException {
        TransactionsGetResponse body = mock(TransactionsGetResponse.class);
        when(body.getTransactions()).thenReturn(transactions);
        Call<TransactionsGetResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.success(body));
        return call;
    }

    private Transaction transaction(String accountId, LocalDate date, double amount,
                                    String primary, String detailed) {
        Transaction tx = mock(Transaction.class);
        when(tx.getAccountId()).thenReturn(accountId);
        // date/amount only reached if transaction passes both filters; use lenient to avoid UnnecessaryStubbing
        lenient().when(tx.getDate()).thenReturn(date);
        lenient().when(tx.getAmount()).thenReturn(amount);
        if (primary != null) {
            PersonalFinanceCategory pfc = mock(PersonalFinanceCategory.class);
            lenient().when(pfc.getPrimary()).thenReturn(primary);
            lenient().when(pfc.getDetailed()).thenReturn(detailed);
            lenient().when(tx.getPersonalFinanceCategory()).thenReturn(pfc);
        } else {
            lenient().when(tx.getPersonalFinanceCategory()).thenReturn(null);
        }
        return tx;
    }

    private User userWithItems(PlaidItem... items) {
        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(List.of(items));
        return user;
    }

    private double dailyTotalFor(MonthlyGlanceResponse response, LocalDate date) {
        return response.dailyTotals().stream()
                .filter(dt -> dt.transactionDate().equals(date.toString()))
                .mapToDouble(MonthlyGlanceResponse.DailyTotal::total)
                .findFirst()
                .orElse(0.0);
    }
}
