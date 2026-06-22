package org.example.service;

import com.plaid.client.model.Transaction;
import com.plaid.client.model.TransactionsGetResponse;
import com.plaid.client.request.PlaidApi;
import org.example.service.PlaidEnvironmentService;
import okhttp3.ResponseBody;
import org.example.entity.PlaidItem;
import org.example.entity.PlaidItemStatus;
import org.example.entity.User;
import org.example.model.TransactionsResponse;
import org.example.plaid.PlaidTokenError;
import org.example.repository.PlaidAccountRepository;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class TransactionsServiceTest {

    @Mock private PlaidEnvironmentService plaidEnvService;
    @Mock private PlaidApi plaidClient;
    @Mock private EncryptionService encryptionService;
    @Mock private UserRepository userRepository;
    @Mock private PlaidAccountRepository plaidAccountRepository;

    private TransactionsService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(plaidEnvService.getClient()).thenReturn(plaidClient);
        service = new TransactionsService(plaidEnvService, encryptionService, userRepository, plaidAccountRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnAllTransactions_whenUserHasPlaidItems() throws IOException {
        PlaidItem item = mock(PlaidItem.class);
        when(item.getStatus()).thenReturn(PlaidItemStatus.HEALTHY);
        when(item.getAccessTokenEnc()).thenReturn("enc");
        when(item.getId()).thenReturn(UUID.randomUUID());
        when(encryptionService.decrypt("enc")).thenReturn("token");
        when(plaidAccountRepository.findHiddenAccountIdsByItemId(any())).thenReturn(Set.of());

        Transaction tx = mock(Transaction.class);
        when(tx.getAccountId()).thenReturn("acct-123");
        when(tx.getDate()).thenReturn(LocalDate.now());
        when(tx.getName()).thenReturn("Coffee Shop");
        when(tx.getAmount()).thenReturn(4.50);
        when(tx.getIsoCurrencyCode()).thenReturn("USD");
        when(tx.getCategory()).thenReturn(List.of("Food"));

        TransactionsGetResponse body = mock(TransactionsGetResponse.class);
        when(body.getTransactions()).thenReturn(List.of(tx));

        Call<TransactionsGetResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.success(body));
        when(plaidClient.transactionsGet(any())).thenReturn(call);

        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(List.of(item));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        TransactionsResponse result = service.getTransactions(userId, 30);

        assertThat(result.transactions()).hasSize(1);
        assertThat(result.transactions().get(0).accountId()).isEqualTo("acct-123");
        assertThat(result.transactions().get(0).name()).isEqualTo("Coffee Shop");
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.relinkRequired()).isEmpty();
    }

    @Test
    void shouldReturnEmptyList_whenUserHasNoPlaidItems() throws IOException {
        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(new ArrayList<>());
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        TransactionsResponse result = service.getTransactions(userId, 30);

        assertThat(result.transactions()).isEmpty();
        assertThat(result.total()).isZero();
        assertThat(result.relinkRequired()).isEmpty();
        verifyNoInteractions(plaidClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldThrowRuntimeException_whenPlaidTransactionsCallFails() throws IOException {
        PlaidItem item = mock(PlaidItem.class);
        when(item.getStatus()).thenReturn(PlaidItemStatus.HEALTHY);
        when(item.getAccessTokenEnc()).thenReturn("enc");
        when(encryptionService.decrypt("enc")).thenReturn("token");

        Call<TransactionsGetResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.error(500, ResponseBody.create(null, "fail")));
        when(plaidClient.transactionsGet(any())).thenReturn(call);

        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(List.of(item));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.getTransactions(userId, 30))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Plaid transactions fetch failed");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRespectDaysParameter_whenCalled() throws IOException {
        PlaidItem item = mock(PlaidItem.class);
        when(item.getStatus()).thenReturn(PlaidItemStatus.HEALTHY);
        when(item.getAccessTokenEnc()).thenReturn("enc");
        when(item.getId()).thenReturn(UUID.randomUUID());
        when(encryptionService.decrypt("enc")).thenReturn("token");
        when(plaidAccountRepository.findHiddenAccountIdsByItemId(any())).thenReturn(Set.of());

        TransactionsGetResponse body = mock(TransactionsGetResponse.class);
        when(body.getTransactions()).thenReturn(new ArrayList<>());
        Call<TransactionsGetResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.success(body));

        ArgumentCaptor<com.plaid.client.model.TransactionsGetRequest> captor =
                ArgumentCaptor.forClass(com.plaid.client.model.TransactionsGetRequest.class);
        when(plaidClient.transactionsGet(captor.capture())).thenReturn(call);

        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(List.of(item));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        service.getTransactions(userId, 7);

        assertThat(captor.getValue().getStartDate()).isEqualTo(LocalDate.now().minusDays(7));
    }

    @Test
    void shouldSkipItemAndBuildRelinkSignal_whenItemStatusIsNeedsReauth() throws IOException {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(userId);

        PlaidItem item = mock(PlaidItem.class);
        when(item.getStatus()).thenReturn(PlaidItemStatus.NEEDS_REAUTH);
        when(item.getOwner()).thenReturn(owner);
        when(item.getId()).thenReturn(UUID.randomUUID());
        when(item.getInstitutionName()).thenReturn("Chase");

        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(List.of(item));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        TransactionsResponse result = service.getTransactions(userId, 30);

        assertThat(result.transactions()).isEmpty();
        assertThat(result.relinkRequired()).hasSize(1);
        assertThat(result.relinkRequired().get(0).errorType()).isEqualTo(PlaidTokenError.LOGIN_REQUIRED);
        verifyNoInteractions(plaidClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldWriteNeedsReauthStatusAndReturnSignal_whenPlaidReturnsItemLoginRequired() throws IOException {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(userId);

        PlaidItem item = mock(PlaidItem.class);
        when(item.getStatus()).thenReturn(PlaidItemStatus.HEALTHY);
        when(item.getAccessTokenEnc()).thenReturn("enc");
        when(item.getOwner()).thenReturn(owner);
        when(item.getId()).thenReturn(UUID.randomUUID());
        when(item.getInstitutionName()).thenReturn("Chase");
        when(encryptionService.decrypt("enc")).thenReturn("token");

        Call<TransactionsGetResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.error(400,
                ResponseBody.create(null, "{\"error_code\":\"ITEM_LOGIN_REQUIRED\"}")));
        when(plaidClient.transactionsGet(any())).thenReturn(call);

        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(List.of(item));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        TransactionsResponse result = service.getTransactions(userId, 30);

        verify(item).setStatus(PlaidItemStatus.NEEDS_REAUTH);
        assertThat(result.transactions()).isEmpty();
        assertThat(result.relinkRequired()).hasSize(1);
        assertThat(result.relinkRequired().get(0).errorType()).isEqualTo(PlaidTokenError.LOGIN_REQUIRED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldWriteInvalidTokenStatusAndReturnSignal_whenPlaidReturnsInvalidAccessToken() throws IOException {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(userId);

        PlaidItem item = mock(PlaidItem.class);
        when(item.getStatus()).thenReturn(PlaidItemStatus.HEALTHY);
        when(item.getAccessTokenEnc()).thenReturn("enc");
        when(item.getOwner()).thenReturn(owner);
        when(item.getId()).thenReturn(UUID.randomUUID());
        when(item.getInstitutionName()).thenReturn("Chase");
        when(encryptionService.decrypt("enc")).thenReturn("token");

        Call<TransactionsGetResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.error(400,
                ResponseBody.create(null, "{\"error_code\":\"INVALID_ACCESS_TOKEN\"}")));
        when(plaidClient.transactionsGet(any())).thenReturn(call);

        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(List.of(item));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        TransactionsResponse result = service.getTransactions(userId, 30);

        verify(item).setStatus(PlaidItemStatus.INVALID_TOKEN);
        assertThat(result.relinkRequired()).hasSize(1);
        assertThat(result.relinkRequired().get(0).errorType()).isEqualTo(PlaidTokenError.INVALID_TOKEN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExcludeTransactions_fromHiddenAccounts() throws IOException {
        UUID itemId = UUID.randomUUID();
        PlaidItem item = mock(PlaidItem.class);
        when(item.getStatus()).thenReturn(PlaidItemStatus.HEALTHY);
        when(item.getAccessTokenEnc()).thenReturn("enc");
        when(item.getId()).thenReturn(itemId);
        when(encryptionService.decrypt("enc")).thenReturn("token");

        Transaction visibleTx = mock(Transaction.class);
        when(visibleTx.getAccountId()).thenReturn("acct-visible");
        when(visibleTx.getDate()).thenReturn(LocalDate.now());
        when(visibleTx.getName()).thenReturn("Grocery Store");
        when(visibleTx.getAmount()).thenReturn(50.0);
        when(visibleTx.getIsoCurrencyCode()).thenReturn("USD");
        when(visibleTx.getCategory()).thenReturn(List.of("Food"));

        Transaction hiddenTx = mock(Transaction.class);
        when(hiddenTx.getAccountId()).thenReturn("acct-hidden");

        TransactionsGetResponse body = mock(TransactionsGetResponse.class);
        when(body.getTransactions()).thenReturn(List.of(visibleTx, hiddenTx));

        Call<TransactionsGetResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.success(body));
        when(plaidClient.transactionsGet(any())).thenReturn(call);

        when(plaidAccountRepository.findHiddenAccountIdsByItemId(itemId))
                .thenReturn(Set.of("acct-hidden"));

        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(List.of(item));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        TransactionsResponse result = service.getTransactions(userId, 30);

        assertThat(result.transactions()).hasSize(1);
        assertThat(result.transactions().get(0).accountId()).isEqualTo("acct-visible");
        assertThat(result.transactions().get(0).name()).isEqualTo("Grocery Store");
        assertThat(result.total()).isEqualTo(1);
    }
}
