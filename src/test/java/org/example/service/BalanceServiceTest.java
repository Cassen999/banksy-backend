package org.example.service;

import com.plaid.client.model.AccountBase;
import com.plaid.client.model.AccountBalance;
import com.plaid.client.model.AccountsGetResponse;
import com.plaid.client.request.PlaidApi;
import okhttp3.ResponseBody;
import org.example.entity.PlaidItem;
import org.example.entity.User;
import org.example.model.BalanceResponse;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock private PlaidApi plaidClient;
    @Mock private EncryptionService encryptionService;
    @Mock private UserRepository userRepository;

    private BalanceService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new BalanceService(plaidClient, encryptionService, userRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnAllBalances_whenUserHasMultiplePlaidItems() throws IOException {
        PlaidItem item = mock(PlaidItem.class);
        when(item.getAccessTokenEnc()).thenReturn("enc-token");
        when(encryptionService.decrypt("enc-token")).thenReturn("access-token");

        AccountBalance balance = mock(AccountBalance.class);
        when(balance.getCurrent()).thenReturn(1000.0);
        when(balance.getAvailable()).thenReturn(900.0);
        when(balance.getIsoCurrencyCode()).thenReturn("USD");

        AccountBase account = mock(AccountBase.class);
        when(account.getName()).thenReturn("Checking");
        when(account.getType()).thenReturn(null);
        when(account.getSubtype()).thenReturn(null);
        when(account.getBalances()).thenReturn(balance);

        AccountsGetResponse body = mock(AccountsGetResponse.class);
        when(body.getAccounts()).thenReturn(List.of(account));

        Call<AccountsGetResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.success(body));
        when(plaidClient.accountsBalanceGet(any())).thenReturn(call);

        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(List.of(item));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        BalanceResponse result = service.getBalance(userId);

        assertThat(result.accounts()).hasSize(1);
        assertThat(result.accounts().get(0).name()).isEqualTo("Checking");
        assertThat(result.accounts().get(0).currentBalance()).isEqualTo(1000.0);
    }

    @Test
    void shouldReturnEmptyList_whenUserHasNoPlaidItems() throws IOException {
        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(new ArrayList<>());
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        BalanceResponse result = service.getBalance(userId);

        assertThat(result.accounts()).isEmpty();
        verifyNoInteractions(plaidClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldThrowRuntimeException_whenPlaidBalanceCallFails() throws IOException {
        PlaidItem item = mock(PlaidItem.class);
        when(item.getAccessTokenEnc()).thenReturn("enc-token");
        when(encryptionService.decrypt("enc-token")).thenReturn("access-token");

        Call<AccountsGetResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.error(500, ResponseBody.create(null, "fail")));
        when(plaidClient.accountsBalanceGet(any())).thenReturn(call);

        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(List.of(item));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.getBalance(userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Plaid balance fetch failed");
    }
}
