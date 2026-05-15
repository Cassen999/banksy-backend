package org.example.service;

import com.plaid.client.model.Transaction;
import com.plaid.client.model.TransactionsGetResponse;
import com.plaid.client.request.PlaidApi;
import okhttp3.ResponseBody;
import org.example.entity.PlaidItem;
import org.example.entity.User;
import org.example.model.TransactionsResponse;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionsServiceTest {

    @Mock private PlaidApi plaidClient;
    @Mock private EncryptionService encryptionService;
    @Mock private UserRepository userRepository;

    private TransactionsService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TransactionsService(plaidClient, encryptionService, userRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnAllTransactions_whenUserHasPlaidItems() throws IOException {
        PlaidItem item = mock(PlaidItem.class);
        when(item.getAccessTokenEnc()).thenReturn("enc");
        when(encryptionService.decrypt("enc")).thenReturn("token");

        Transaction tx = mock(Transaction.class);
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
        assertThat(result.transactions().get(0).name()).isEqualTo("Coffee Shop");
        assertThat(result.total()).isEqualTo(1);
    }

    @Test
    void shouldReturnEmptyList_whenUserHasNoPlaidItems() throws IOException {
        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(new ArrayList<>());
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        TransactionsResponse result = service.getTransactions(userId, 30);

        assertThat(result.transactions()).isEmpty();
        assertThat(result.total()).isZero();
        verifyNoInteractions(plaidClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldThrowRuntimeException_whenPlaidTransactionsCallFails() throws IOException {
        PlaidItem item = mock(PlaidItem.class);
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
        when(item.getAccessTokenEnc()).thenReturn("enc");
        when(encryptionService.decrypt("enc")).thenReturn("token");

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

        LocalDate expectedStart = LocalDate.now().minusDays(7);
        assertThat(captor.getValue().getStartDate()).isEqualTo(expectedStart);
    }
}
