package org.example.service;

import com.plaid.client.model.PersonalFinanceCategory;
import com.plaid.client.model.RecurringTransactionFrequency;
import com.plaid.client.model.TransactionStream;
import com.plaid.client.model.TransactionStreamAmount;
import com.plaid.client.model.TransactionStreamStatus;
import com.plaid.client.model.TransactionsRecurringGetResponse;
import com.plaid.client.request.PlaidApi;
import okhttp3.ResponseBody;
import org.example.entity.PlaidAccount;
import org.example.entity.PlaidItem;
import org.example.entity.PlaidItemStatus;
import org.example.entity.User;
import org.example.model.RecurringResponse;
import org.example.model.ScheduledDepositDto;
import org.example.plaid.PlaidTokenError;
import org.example.repository.PlaidAccountRepository;
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
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecurringServiceTest {

    @Mock private PlaidEnvironmentService plaidEnvService;
    @Mock private PlaidApi plaidClient;
    @Mock private EncryptionService encryptionService;
    @Mock private UserRepository userRepository;
    @Mock private PlaidAccountRepository plaidAccountRepository;

    private RecurringService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(plaidEnvService.getClient()).thenReturn(plaidClient);
        service = new RecurringService(plaidEnvService, encryptionService, userRepository, plaidAccountRepository);
    }

    // --- get-all mode ---

    @Test
    @SuppressWarnings("unchecked")
    void shouldAggregateStreams_whenUserHasMultipleHealthyItems() throws IOException {
        PlaidItem item1 = healthyItem("enc-token-1");
        PlaidItem item2 = healthyItem("enc-token-2");
        when(encryptionService.decrypt("enc-token-1")).thenReturn("token-1");
        when(encryptionService.decrypt("enc-token-2")).thenReturn("token-2");

        TransactionStream inflow1 = stream("acct-1", "Netflix");
        TransactionStream outflow1 = stream("acct-1", "Paycheck");
        TransactionStream inflow2 = stream("acct-2", "Spotify");

        Call<TransactionsRecurringGetResponse> call1 = buildCall(List.of(inflow1), List.of(outflow1));
        Call<TransactionsRecurringGetResponse> call2 = buildCall(List.of(inflow2), List.of());
        when(plaidClient.transactionsRecurringGet(any())).thenReturn(call1).thenReturn(call2);

        User user = userWithItems(item1, item2);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        RecurringResponse result = service.getRecurring(userId, null);

        assertThat(result.inflowStreams()).hasSize(2);
        assertThat(result.outflowStreams()).hasSize(1);
        assertThat(result.relinkRequired()).isEmpty();
    }

    @Test
    void shouldReturnEmptyStreams_whenUserHasNoItems() throws IOException {
        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(new ArrayList<>());
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        RecurringResponse result = service.getRecurring(userId, null);

        assertThat(result.inflowStreams()).isEmpty();
        assertThat(result.outflowStreams()).isEmpty();
        assertThat(result.relinkRequired()).isEmpty();
        verifyNoInteractions(plaidClient);
    }

    @Test
    void shouldSkipItemAndAddRelinkSignal_whenItemStatusIsNeedsReauth() throws IOException {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(userId);

        PlaidItem item = mock(PlaidItem.class);
        when(item.getStatus()).thenReturn(PlaidItemStatus.NEEDS_REAUTH);
        when(item.getOwner()).thenReturn(owner);
        when(item.getId()).thenReturn(UUID.randomUUID());
        when(item.getInstitutionName()).thenReturn("Chase");

        User user = userWithItems(item);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        RecurringResponse result = service.getRecurring(userId, null);

        assertThat(result.inflowStreams()).isEmpty();
        assertThat(result.relinkRequired()).hasSize(1);
        assertThat(result.relinkRequired().get(0).errorType()).isEqualTo(PlaidTokenError.LOGIN_REQUIRED);
        verifyNoInteractions(plaidClient);
    }

    @Test
    void shouldSkipItemAndAddRelinkSignal_whenItemStatusIsInvalidToken() throws IOException {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(userId);

        PlaidItem item = mock(PlaidItem.class);
        when(item.getStatus()).thenReturn(PlaidItemStatus.INVALID_TOKEN);
        when(item.getOwner()).thenReturn(owner);
        when(item.getId()).thenReturn(UUID.randomUUID());
        when(item.getInstitutionName()).thenReturn("Wells Fargo");

        User user = userWithItems(item);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        RecurringResponse result = service.getRecurring(userId, null);

        assertThat(result.inflowStreams()).isEmpty();
        assertThat(result.relinkRequired()).hasSize(1);
        assertThat(result.relinkRequired().get(0).errorType()).isEqualTo(PlaidTokenError.INVALID_TOKEN);
        verifyNoInteractions(plaidClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldUpdateStatusAndAddRelinkSignal_whenPlaidReturnsItemLoginRequired() throws IOException {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(userId);

        PlaidItem item = healthyItem("enc-token");
        when(item.getOwner()).thenReturn(owner);
        when(item.getInstitutionName()).thenReturn("Chase");
        when(encryptionService.decrypt("enc-token")).thenReturn("access-token");

        Call<TransactionsRecurringGetResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.error(400,
                ResponseBody.create(null, "{\"error_code\":\"ITEM_LOGIN_REQUIRED\"}")));
        when(plaidClient.transactionsRecurringGet(any())).thenReturn(call);

        User user = userWithItems(item);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        RecurringResponse result = service.getRecurring(userId, null);

        verify(item).setStatus(PlaidItemStatus.NEEDS_REAUTH);
        assertThat(result.inflowStreams()).isEmpty();
        assertThat(result.relinkRequired()).hasSize(1);
        assertThat(result.relinkRequired().get(0).errorType()).isEqualTo(PlaidTokenError.LOGIN_REQUIRED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldUpdateStatusAndAddRelinkSignal_whenPlaidReturnsInvalidAccessToken() throws IOException {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(userId);

        PlaidItem item = healthyItem("enc-token");
        when(item.getOwner()).thenReturn(owner);
        when(item.getInstitutionName()).thenReturn("Chase");
        when(encryptionService.decrypt("enc-token")).thenReturn("access-token");

        Call<TransactionsRecurringGetResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.error(400,
                ResponseBody.create(null, "{\"error_code\":\"INVALID_ACCESS_TOKEN\"}")));
        when(plaidClient.transactionsRecurringGet(any())).thenReturn(call);

        User user = userWithItems(item);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        RecurringResponse result = service.getRecurring(userId, null);

        verify(item).setStatus(PlaidItemStatus.INVALID_TOKEN);
        assertThat(result.relinkRequired()).hasSize(1);
        assertThat(result.relinkRequired().get(0).errorType()).isEqualTo(PlaidTokenError.INVALID_TOKEN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldThrowRuntimeException_whenPlaidReturnsNonTokenError() throws IOException {
        PlaidItem item = healthyItem("enc-token");
        when(encryptionService.decrypt("enc-token")).thenReturn("access-token");

        Call<TransactionsRecurringGetResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.error(500, ResponseBody.create(null, "fail")));
        when(plaidClient.transactionsRecurringGet(any())).thenReturn(call);

        User user = userWithItems(item);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.getRecurring(userId, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Plaid recurring fetch failed");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnHealthyStreamsAlongside_whenMixOfHealthyAndUnhealthyItems() throws IOException {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(userId);

        PlaidItem unhealthyItem = mock(PlaidItem.class);
        when(unhealthyItem.getStatus()).thenReturn(PlaidItemStatus.NEEDS_REAUTH);
        when(unhealthyItem.getOwner()).thenReturn(owner);
        when(unhealthyItem.getId()).thenReturn(UUID.randomUUID());
        when(unhealthyItem.getInstitutionName()).thenReturn("Bad Bank");

        PlaidItem healthyItem = healthyItem("enc-token");
        when(encryptionService.decrypt("enc-token")).thenReturn("access-token");

        TransactionStream inflow = stream("acct-1", "Direct Deposit");
        Call<TransactionsRecurringGetResponse> call = buildCall(List.of(inflow), List.of());
        when(plaidClient.transactionsRecurringGet(any())).thenReturn(call);

        User user = userWithItems(unhealthyItem, healthyItem);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        RecurringResponse result = service.getRecurring(userId, null);

        assertThat(result.inflowStreams()).hasSize(1);
        assertThat(result.outflowStreams()).isEmpty();
        assertThat(result.relinkRequired()).hasSize(1);
        assertThat(result.relinkRequired().get(0).institutionName()).isEqualTo("Bad Bank");
    }

    // --- per-account mode ---

    @Test
    void shouldThrowNoSuchElementException_whenAccountIdNotFound() {
        when(plaidAccountRepository.findByPlaidAccountIdWithItem("unknown-id"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRecurring(userId, "unknown-id"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void shouldThrowSecurityException_whenUserNotLinkedToItem() {
        UUID itemId = UUID.randomUUID();
        PlaidItem item = mock(PlaidItem.class);
        when(item.getId()).thenReturn(itemId);

        PlaidAccount account = mock(PlaidAccount.class);
        when(account.getPlaidItem()).thenReturn(item);
        when(plaidAccountRepository.findByPlaidAccountIdWithItem("acct-123"))
                .thenReturn(Optional.of(account));

        PlaidItem otherItem = mock(PlaidItem.class);
        when(otherItem.getId()).thenReturn(UUID.randomUUID());

        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(List.of(otherItem));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.getRecurring(userId, "acct-123"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void shouldReturnRelinkSignal_whenItemIsNotHealthy() throws IOException {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(userId);

        UUID itemId = UUID.randomUUID();
        PlaidItem item = mock(PlaidItem.class);
        when(item.getId()).thenReturn(itemId);
        when(item.getStatus()).thenReturn(PlaidItemStatus.NEEDS_REAUTH);
        when(item.getOwner()).thenReturn(owner);
        when(item.getInstitutionName()).thenReturn("Chase");

        PlaidAccount account = mock(PlaidAccount.class);
        when(account.getPlaidItem()).thenReturn(item);
        when(plaidAccountRepository.findByPlaidAccountIdWithItem("acct-123"))
                .thenReturn(Optional.of(account));

        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(List.of(item));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        RecurringResponse result = service.getRecurring(userId, "acct-123");

        assertThat(result.inflowStreams()).isEmpty();
        assertThat(result.outflowStreams()).isEmpty();
        assertThat(result.relinkRequired()).hasSize(1);
        assertThat(result.relinkRequired().get(0).errorType()).isEqualTo(PlaidTokenError.LOGIN_REQUIRED);
        verifyNoInteractions(plaidClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnFilteredStreams_whenAccountIdIsValid() throws IOException {
        UUID itemId = UUID.randomUUID();
        PlaidItem item = healthyItem("enc-token");
        when(item.getId()).thenReturn(itemId);
        when(encryptionService.decrypt("enc-token")).thenReturn("access-token");

        PlaidAccount account = mock(PlaidAccount.class);
        when(account.getPlaidItem()).thenReturn(item);
        when(plaidAccountRepository.findByPlaidAccountIdWithItem("acct-123"))
                .thenReturn(Optional.of(account));

        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(List.of(item));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        TransactionStream outflow = stream("acct-123", "Netflix");
        Call<TransactionsRecurringGetResponse> call = buildCall(List.of(), List.of(outflow));
        when(plaidClient.transactionsRecurringGet(any())).thenReturn(call);

        RecurringResponse result = service.getRecurring(userId, "acct-123");

        assertThat(result.inflowStreams()).isEmpty();
        assertThat(result.outflowStreams()).hasSize(1);
        assertThat(result.outflowStreams().get(0).merchantName()).isEqualTo("Netflix");
        assertThat(result.relinkRequired()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldUpdateStatusAndAddRelinkSignal_whenPerAccountPlaidTokenError() throws IOException {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(userId);

        UUID itemId = UUID.randomUUID();
        PlaidItem item = healthyItem("enc-token");
        when(item.getId()).thenReturn(itemId);
        when(item.getOwner()).thenReturn(owner);
        when(item.getInstitutionName()).thenReturn("Chase");
        when(encryptionService.decrypt("enc-token")).thenReturn("access-token");

        PlaidAccount account = mock(PlaidAccount.class);
        when(account.getPlaidItem()).thenReturn(item);
        when(plaidAccountRepository.findByPlaidAccountIdWithItem("acct-123"))
                .thenReturn(Optional.of(account));

        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(List.of(item));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        Call<TransactionsRecurringGetResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.error(400,
                ResponseBody.create(null, "{\"error_code\":\"ITEM_LOGIN_REQUIRED\"}")));
        when(plaidClient.transactionsRecurringGet(any())).thenReturn(call);

        RecurringResponse result = service.getRecurring(userId, "acct-123");

        verify(item).setStatus(PlaidItemStatus.NEEDS_REAUTH);
        assertThat(result.inflowStreams()).isEmpty();
        assertThat(result.relinkRequired()).hasSize(1);
        assertThat(result.relinkRequired().get(0).errorType()).isEqualTo(PlaidTokenError.LOGIN_REQUIRED);
    }

    // --- scheduled-deposits mode ---

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnCurrentMonthInflowStreams_whenActiveStreamsExist() throws IOException {
        LocalDate today = LocalDate.now();
        LocalDate endOfMonth = YearMonth.now().atEndOfMonth();
        TransactionStream s1 = streamWithDate("Paycheck", today, true);
        TransactionStream s2 = streamWithDate("Freelance", endOfMonth, true);
        Call<TransactionsRecurringGetResponse> call = buildCall(List.of(s1, s2), List.of());
        PlaidItem item = healthyItem("enc-token");
        User user = userWithItems(item);

        when(encryptionService.decrypt("enc-token")).thenReturn("token");
        when(plaidClient.transactionsRecurringGet(any())).thenReturn(call);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        List<ScheduledDepositDto> result = service.getScheduledDeposits(userId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ScheduledDepositDto::merchantName)
                .containsExactlyInAnyOrder("Paycheck", "Freelance");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExcludeOutflowStreams() throws IOException {
        LocalDate today = LocalDate.now();
        TransactionStream inflow = streamWithDate("Paycheck", today, true);
        TransactionStream outflow = streamWithDate("Netflix", today, true);
        Call<TransactionsRecurringGetResponse> call = buildCall(List.of(inflow), List.of(outflow));
        PlaidItem item = healthyItem("enc-token");
        User user = userWithItems(item);

        when(encryptionService.decrypt("enc-token")).thenReturn("token");
        when(plaidClient.transactionsRecurringGet(any())).thenReturn(call);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        List<ScheduledDepositDto> result = service.getScheduledDeposits(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).merchantName()).isEqualTo("Paycheck");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExcludeInactiveStreams() throws IOException {
        TransactionStream inactive = streamWithDate("Paycheck", LocalDate.now(), false);
        Call<TransactionsRecurringGetResponse> call = buildCall(List.of(inactive), List.of());
        PlaidItem item = healthyItem("enc-token");
        User user = userWithItems(item);

        when(encryptionService.decrypt("enc-token")).thenReturn("token");
        when(plaidClient.transactionsRecurringGet(any())).thenReturn(call);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        assertThat(service.getScheduledDeposits(userId)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExcludeStreamsWithNullPredictedNextDate() throws IOException {
        TransactionStream nullDate = streamWithDate("Paycheck", null, true);
        Call<TransactionsRecurringGetResponse> call = buildCall(List.of(nullDate), List.of());
        PlaidItem item = healthyItem("enc-token");
        User user = userWithItems(item);

        when(encryptionService.decrypt("enc-token")).thenReturn("token");
        when(plaidClient.transactionsRecurringGet(any())).thenReturn(call);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        assertThat(service.getScheduledDeposits(userId)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExcludeStreamsWithPredictedNextDateInPast() throws IOException {
        TransactionStream past = streamWithDate("Paycheck", LocalDate.now().minusDays(1), true);
        Call<TransactionsRecurringGetResponse> call = buildCall(List.of(past), List.of());
        PlaidItem item = healthyItem("enc-token");
        User user = userWithItems(item);

        when(encryptionService.decrypt("enc-token")).thenReturn("token");
        when(plaidClient.transactionsRecurringGet(any())).thenReturn(call);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        assertThat(service.getScheduledDeposits(userId)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExcludeStreamsWithPredictedNextDateInFutureMonth() throws IOException {
        TransactionStream future = streamWithDate("Paycheck", YearMonth.now().plusMonths(1).atDay(1), true);
        Call<TransactionsRecurringGetResponse> call = buildCall(List.of(future), List.of());
        PlaidItem item = healthyItem("enc-token");
        User user = userWithItems(item);

        when(encryptionService.decrypt("enc-token")).thenReturn("token");
        when(plaidClient.transactionsRecurringGet(any())).thenReturn(call);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        assertThat(service.getScheduledDeposits(userId)).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnEmpty_whenNoStreamsMatchCurrentMonth() throws IOException {
        TransactionStream pastStream = streamWithDate("Past", LocalDate.now().minusDays(10), true);
        TransactionStream futureStream = streamWithDate("Future", YearMonth.now().plusMonths(1).atDay(1), true);
        Call<TransactionsRecurringGetResponse> call = buildCall(List.of(pastStream, futureStream), List.of());
        PlaidItem item = healthyItem("enc-token");
        User user = userWithItems(item);

        when(encryptionService.decrypt("enc-token")).thenReturn("token");
        when(plaidClient.transactionsRecurringGet(any())).thenReturn(call);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        assertThat(service.getScheduledDeposits(userId)).isEmpty();
    }

    @Test
    void shouldReturnEmpty_whenUserHasNoItems_scheduledDeposits() throws IOException {
        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(new ArrayList<>());
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        assertThat(service.getScheduledDeposits(userId)).isEmpty();
        verifyNoInteractions(plaidClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSkipUnhealthyItems_andStillReturnStreamsFromHealthyOnes_scheduledDeposits() throws IOException {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(userId);

        PlaidItem unhealthy = mock(PlaidItem.class);
        when(unhealthy.getStatus()).thenReturn(PlaidItemStatus.NEEDS_REAUTH);
        when(unhealthy.getOwner()).thenReturn(owner);
        when(unhealthy.getId()).thenReturn(UUID.randomUUID());
        when(unhealthy.getInstitutionName()).thenReturn("Bad Bank");

        TransactionStream deposit = streamWithDate("Paycheck", LocalDate.now(), true);
        Call<TransactionsRecurringGetResponse> call = buildCall(List.of(deposit), List.of());
        PlaidItem healthy = healthyItem("enc-token");
        User user = userWithItems(unhealthy, healthy);

        when(encryptionService.decrypt("enc-token")).thenReturn("token");
        when(plaidClient.transactionsRecurringGet(any())).thenReturn(call);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        List<ScheduledDepositDto> result = service.getScheduledDeposits(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).merchantName()).isEqualTo("Paycheck");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSortByPredictedNextDateAscending() throws IOException {
        LocalDate earlier = LocalDate.now();
        LocalDate later = YearMonth.now().atEndOfMonth();
        TransactionStream sLater = streamWithDate("Later", later, true);
        TransactionStream sEarlier = streamWithDate("Earlier", earlier, true);
        Call<TransactionsRecurringGetResponse> call = buildCall(List.of(sLater, sEarlier), List.of());
        PlaidItem item = healthyItem("enc-token");
        User user = userWithItems(item);

        when(encryptionService.decrypt("enc-token")).thenReturn("token");
        when(plaidClient.transactionsRecurringGet(any())).thenReturn(call);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        List<ScheduledDepositDto> result = service.getScheduledDeposits(userId);

        assertThat(result).hasSizeGreaterThanOrEqualTo(1);
        for (int i = 0; i < result.size() - 1; i++) {
            assertThat(result.get(i).predictedNextDate())
                    .isBeforeOrEqualTo(result.get(i + 1).predictedNextDate());
        }
    }

    // --- helpers ---

    @SuppressWarnings("unchecked")
    private PlaidItem healthyItem(String encToken) {
        PlaidItem item = mock(PlaidItem.class);
        when(item.getStatus()).thenReturn(PlaidItemStatus.HEALTHY);
        when(item.getAccessTokenEnc()).thenReturn(encToken);
        lenient().when(item.getId()).thenReturn(UUID.randomUUID());
        return item;
    }

    private TransactionStream stream(String accountId, String merchantName) {
        TransactionStream s = mock(TransactionStream.class);
        when(s.getAccountId()).thenReturn(accountId);
        when(s.getStreamId()).thenReturn(UUID.randomUUID().toString());
        when(s.getMerchantName()).thenReturn(merchantName);
        when(s.getDescription()).thenReturn(merchantName);
        when(s.getFrequency()).thenReturn(RecurringTransactionFrequency.MONTHLY);
        when(s.getFirstDate()).thenReturn(null);
        when(s.getLastDate()).thenReturn(null);
        when(s.getPredictedNextDate()).thenReturn(null);
        when(s.getAverageAmount()).thenReturn(null);
        when(s.getLastAmount()).thenReturn(null);
        when(s.getIsActive()).thenReturn(true);
        when(s.getPersonalFinanceCategory()).thenReturn(null);
        when(s.getStatus()).thenReturn(TransactionStreamStatus.MATURE);
        return s;
    }

    @SuppressWarnings("unchecked")
    private Call<TransactionsRecurringGetResponse> buildCall(
            List<TransactionStream> inflow, List<TransactionStream> outflow) throws IOException {
        TransactionsRecurringGetResponse body = mock(TransactionsRecurringGetResponse.class);
        when(body.getInflowStreams()).thenReturn(inflow);
        when(body.getOutflowStreams()).thenReturn(outflow);
        Call<TransactionsRecurringGetResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.success(body));
        return call;
    }

    private User userWithItems(PlaidItem... items) {
        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(List.of(items));
        return user;
    }

    private TransactionStream streamWithDate(String merchantName, LocalDate predictedNextDate, boolean isActive) {
        TransactionStream s = mock(TransactionStream.class);
        when(s.getAccountId()).thenReturn("acct-1");
        when(s.getStreamId()).thenReturn(UUID.randomUUID().toString());
        when(s.getMerchantName()).thenReturn(merchantName);
        when(s.getDescription()).thenReturn(merchantName);
        when(s.getFrequency()).thenReturn(RecurringTransactionFrequency.MONTHLY);
        when(s.getFirstDate()).thenReturn(null);
        when(s.getLastDate()).thenReturn(null);
        when(s.getPredictedNextDate()).thenReturn(predictedNextDate);
        when(s.getAverageAmount()).thenReturn(null);
        when(s.getLastAmount()).thenReturn(null);
        when(s.getIsActive()).thenReturn(isActive);
        when(s.getPersonalFinanceCategory()).thenReturn(null);
        when(s.getStatus()).thenReturn(TransactionStreamStatus.MATURE);
        return s;
    }

}
