package org.example.service;

import com.plaid.client.model.AccountBase;
import com.plaid.client.model.AccountBalance;
import com.plaid.client.model.AccountsGetResponse;
import com.plaid.client.request.PlaidApi;
import okhttp3.ResponseBody;
import org.example.entity.PlaidItem;
import org.example.entity.PlaidItemStatus;
import org.example.entity.User;
import org.example.model.BalanceResponse;
import org.example.plaid.PlaidTokenError;
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
        when(item.getStatus()).thenReturn(PlaidItemStatus.HEALTHY);
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
        assertThat(result.relinkRequired()).isEmpty();
    }

    @Test
    void shouldReturnEmptyList_whenUserHasNoPlaidItems() throws IOException {
        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(new ArrayList<>());
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        BalanceResponse result = service.getBalance(userId);

        assertThat(result.accounts()).isEmpty();
        assertThat(result.relinkRequired()).isEmpty();
        verifyNoInteractions(plaidClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldThrowRuntimeException_whenPlaidBalanceCallFails() throws IOException {
        PlaidItem item = mock(PlaidItem.class);
        when(item.getStatus()).thenReturn(PlaidItemStatus.HEALTHY);
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

        BalanceResponse result = service.getBalance(userId);

        assertThat(result.accounts()).isEmpty();
        assertThat(result.relinkRequired()).hasSize(1);
        assertThat(result.relinkRequired().get(0).errorType()).isEqualTo(PlaidTokenError.LOGIN_REQUIRED);
        assertThat(result.relinkRequired().get(0).canRelink()).isTrue();
        verifyNoInteractions(plaidClient);
    }

    @Test
    void shouldSkipItemAndBuildRelinkSignal_whenItemStatusIsInvalidToken() throws IOException {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(userId);

        PlaidItem item = mock(PlaidItem.class);
        when(item.getStatus()).thenReturn(PlaidItemStatus.INVALID_TOKEN);
        when(item.getOwner()).thenReturn(owner);
        when(item.getId()).thenReturn(UUID.randomUUID());
        when(item.getInstitutionName()).thenReturn("Wells Fargo");

        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(List.of(item));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        BalanceResponse result = service.getBalance(userId);

        assertThat(result.accounts()).isEmpty();
        assertThat(result.relinkRequired()).hasSize(1);
        assertThat(result.relinkRequired().get(0).errorType()).isEqualTo(PlaidTokenError.INVALID_TOKEN);
        verifyNoInteractions(plaidClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldWriteNeedsReauthStatusAndReturnSignal_whenPlaidReturnsItemLoginRequired() throws IOException {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(userId);

        PlaidItem item = mock(PlaidItem.class);
        when(item.getStatus()).thenReturn(PlaidItemStatus.HEALTHY);
        when(item.getAccessTokenEnc()).thenReturn("enc-token");
        when(item.getOwner()).thenReturn(owner);
        when(item.getId()).thenReturn(UUID.randomUUID());
        when(item.getInstitutionName()).thenReturn("Chase");
        when(encryptionService.decrypt("enc-token")).thenReturn("access-token");

        Call<AccountsGetResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.error(400,
                ResponseBody.create(null, "{\"error_code\":\"ITEM_LOGIN_REQUIRED\"}")));
        when(plaidClient.accountsBalanceGet(any())).thenReturn(call);

        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(List.of(item));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        BalanceResponse result = service.getBalance(userId);

        verify(item).setStatus(PlaidItemStatus.NEEDS_REAUTH);
        assertThat(result.accounts()).isEmpty();
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
        when(item.getAccessTokenEnc()).thenReturn("enc-token");
        when(item.getOwner()).thenReturn(owner);
        when(item.getId()).thenReturn(UUID.randomUUID());
        when(item.getInstitutionName()).thenReturn("Chase");
        when(encryptionService.decrypt("enc-token")).thenReturn("access-token");

        Call<AccountsGetResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.error(400,
                ResponseBody.create(null, "{\"error_code\":\"INVALID_ACCESS_TOKEN\"}")));
        when(plaidClient.accountsBalanceGet(any())).thenReturn(call);

        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(List.of(item));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        BalanceResponse result = service.getBalance(userId);

        verify(item).setStatus(PlaidItemStatus.INVALID_TOKEN);
        assertThat(result.accounts()).isEmpty();
        assertThat(result.relinkRequired()).hasSize(1);
        assertThat(result.relinkRequired().get(0).errorType()).isEqualTo(PlaidTokenError.INVALID_TOKEN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnHealthyAccountsAlongside_whenMixOfHealthyAndUnhealthyItems() throws IOException {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(userId);

        PlaidItem unhealthyItem = mock(PlaidItem.class);
        when(unhealthyItem.getStatus()).thenReturn(PlaidItemStatus.NEEDS_REAUTH);
        when(unhealthyItem.getOwner()).thenReturn(owner);
        when(unhealthyItem.getId()).thenReturn(UUID.randomUUID());
        when(unhealthyItem.getInstitutionName()).thenReturn("Bad Bank");

        PlaidItem healthyItem = mock(PlaidItem.class);
        when(healthyItem.getStatus()).thenReturn(PlaidItemStatus.HEALTHY);
        when(healthyItem.getAccessTokenEnc()).thenReturn("enc-token");
        when(encryptionService.decrypt("enc-token")).thenReturn("access-token");

        AccountBalance balance = mock(AccountBalance.class);
        when(balance.getCurrent()).thenReturn(500.0);
        when(balance.getAvailable()).thenReturn(500.0);
        when(balance.getIsoCurrencyCode()).thenReturn("USD");
        AccountBase account = mock(AccountBase.class);
        when(account.getName()).thenReturn("Savings");
        when(account.getType()).thenReturn(null);
        when(account.getSubtype()).thenReturn(null);
        when(account.getBalances()).thenReturn(balance);
        AccountsGetResponse body = mock(AccountsGetResponse.class);
        when(body.getAccounts()).thenReturn(List.of(account));
        Call<AccountsGetResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.success(body));
        when(plaidClient.accountsBalanceGet(any())).thenReturn(call);

        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(List.of(unhealthyItem, healthyItem));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        BalanceResponse result = service.getBalance(userId);

        assertThat(result.accounts()).hasSize(1);
        assertThat(result.accounts().get(0).name()).isEqualTo("Savings");
        assertThat(result.relinkRequired()).hasSize(1);
        assertThat(result.relinkRequired().get(0).institutionName()).isEqualTo("Bad Bank");
    }
}
