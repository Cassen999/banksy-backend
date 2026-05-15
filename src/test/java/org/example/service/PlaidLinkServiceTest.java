package org.example.service;

import com.plaid.client.model.*;
import com.plaid.client.request.PlaidApi;
import okhttp3.ResponseBody;
import org.example.entity.PlaidAccount;
import org.example.entity.PlaidItem;
import org.example.entity.User;
import org.example.repository.PlaidAccountRepository;
import org.example.repository.PlaidItemRepository;
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
class PlaidLinkServiceTest {

    @Mock private PlaidApi plaidClient;
    @Mock private EncryptionService encryptionService;
    @Mock private PlaidItemRepository plaidItemRepository;
    @Mock private PlaidAccountRepository plaidAccountRepository;
    @Mock private UserRepository userRepository;

    private PlaidLinkService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PlaidLinkService(plaidClient, encryptionService,
                plaidItemRepository, plaidAccountRepository, userRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnLinkToken_whenPlaidRespondsSuccessfully() throws IOException {
        LinkTokenCreateResponse body = mock(LinkTokenCreateResponse.class);
        when(body.getLinkToken()).thenReturn("link-token-xyz");
        Call<LinkTokenCreateResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.success(body));
        when(plaidClient.linkTokenCreate(any())).thenReturn(call);

        String token = service.createLinkToken(userId);

        assertThat(token).isEqualTo("link-token-xyz");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldThrowRuntimeException_whenPlaidLinkTokenCallFails() throws IOException {
        Call<LinkTokenCreateResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.error(500, ResponseBody.create(null, "error")));
        when(plaidClient.linkTokenCreate(any())).thenReturn(call);

        assertThatThrownBy(() -> service.createLinkToken(userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to create Plaid link token");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldCreateNewItemAndAccounts_whenPublicTokenIsNew() throws IOException {
        User user = userWithItems(new ArrayList<>());
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        ItemPublicTokenExchangeResponse exchangeBody = mock(ItemPublicTokenExchangeResponse.class);
        when(exchangeBody.getAccessToken()).thenReturn("access-token");
        when(exchangeBody.getItemId()).thenReturn("item-id-new");
        Call<ItemPublicTokenExchangeResponse> exchangeCall = mock(Call.class);
        when(exchangeCall.execute()).thenReturn(Response.success(exchangeBody));
        when(plaidClient.itemPublicTokenExchange(any())).thenReturn(exchangeCall);

        when(plaidItemRepository.findByItemId("item-id-new")).thenReturn(Optional.empty());
        when(encryptionService.encrypt("access-token")).thenReturn("enc-token");
        PlaidItem savedItem = new PlaidItem();
        when(plaidItemRepository.save(any())).thenReturn(savedItem);

        AccountBase account = mock(AccountBase.class);
        when(account.getAccountId()).thenReturn("acct-1");
        when(account.getName()).thenReturn("Checking");
        AccountType type = mock(AccountType.class);
        when(type.getValue()).thenReturn("depository");
        when(account.getType()).thenReturn(type);
        AccountsGetResponse accountsBody = mock(AccountsGetResponse.class);
        when(accountsBody.getAccounts()).thenReturn(List.of(account));
        Call<AccountsGetResponse> accountsCall = mock(Call.class);
        when(accountsCall.execute()).thenReturn(Response.success(accountsBody));
        when(plaidClient.accountsGet(any())).thenReturn(accountsCall);
        when(plaidAccountRepository.existsByPlaidAccountId("acct-1")).thenReturn(false);

        service.exchangeAndStore("public-token", "ins-1", "Test Bank", userId);

        verify(plaidItemRepository).save(any(PlaidItem.class));
        verify(plaidAccountRepository).save(any(PlaidAccount.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnExistingItem_whenItemIdAlreadyExistsAndUserOwnsIt() throws IOException {
        PlaidItem existingItem = new PlaidItem();
        User user = userWithItems(new ArrayList<>(List.of(existingItem)));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        ItemPublicTokenExchangeResponse exchangeBody = mock(ItemPublicTokenExchangeResponse.class);
        when(exchangeBody.getAccessToken()).thenReturn("access-token");
        when(exchangeBody.getItemId()).thenReturn("item-id-existing");
        Call<ItemPublicTokenExchangeResponse> exchangeCall = mock(Call.class);
        when(exchangeCall.execute()).thenReturn(Response.success(exchangeBody));
        when(plaidClient.itemPublicTokenExchange(any())).thenReturn(exchangeCall);
        when(plaidItemRepository.findByItemId("item-id-existing")).thenReturn(Optional.of(existingItem));

        PlaidItem result = service.exchangeAndStore("public-token", "ins-1", "Test Bank", userId);

        assertThat(result).isEqualTo(existingItem);
        verify(plaidItemRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldThrowRuntimeException_whenTokenExchangeFails() throws IOException {
        User user = mock(User.class);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        Call<ItemPublicTokenExchangeResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.error(500, ResponseBody.create(null, "error")));
        when(plaidClient.itemPublicTokenExchange(any())).thenReturn(call);

        assertThatThrownBy(() -> service.exchangeAndStore("bad-token", "ins-1", "Bank", userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to exchange public token");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSkipDuplicateAccount_whenPlaidAccountIdAlreadyExists() throws IOException {
        User user = userWithItems(new ArrayList<>());
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        ItemPublicTokenExchangeResponse exchangeBody = mock(ItemPublicTokenExchangeResponse.class);
        when(exchangeBody.getAccessToken()).thenReturn("access-token");
        when(exchangeBody.getItemId()).thenReturn("item-id-new");
        Call<ItemPublicTokenExchangeResponse> exchangeCall = mock(Call.class);
        when(exchangeCall.execute()).thenReturn(Response.success(exchangeBody));
        when(plaidClient.itemPublicTokenExchange(any())).thenReturn(exchangeCall);
        when(plaidItemRepository.findByItemId("item-id-new")).thenReturn(Optional.empty());
        when(encryptionService.encrypt(any())).thenReturn("enc");
        when(plaidItemRepository.save(any())).thenReturn(new PlaidItem());

        AccountBase account = mock(AccountBase.class);
        when(account.getAccountId()).thenReturn("acct-existing");
        AccountsGetResponse accountsBody = mock(AccountsGetResponse.class);
        when(accountsBody.getAccounts()).thenReturn(List.of(account));
        Call<AccountsGetResponse> accountsCall = mock(Call.class);
        when(accountsCall.execute()).thenReturn(Response.success(accountsBody));
        when(plaidClient.accountsGet(any())).thenReturn(accountsCall);
        when(plaidAccountRepository.existsByPlaidAccountId("acct-existing")).thenReturn(true);

        service.exchangeAndStore("public-token", "ins-1", "Bank", userId);

        verify(plaidAccountRepository, never()).save(any());
    }

    @Test
    void shouldShareItem_whenRequestingUserOwnsIt() {
        UUID itemId = UUID.randomUUID();
        PlaidItem item = plaidItemWithId(itemId);
        User requestingUser = userWithItems(new ArrayList<>(List.of(item)));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(requestingUser));
        when(plaidItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        UUID targetId = UUID.randomUUID();
        User targetUser = userWithItems(new ArrayList<>());
        when(userRepository.findByEmail("target@example.com")).thenReturn(Optional.of(targetUser));
        when(userRepository.findByIdWithPlaidItems(targetId)).thenReturn(Optional.of(targetUser));
        targetUser.getPlaidItems(); // ensure list exists

        // Replicate ID lookup: findByEmail returns user with targetId
        User targetWithId = mock(User.class);
        when(targetWithId.getId()).thenReturn(targetId);
        when(userRepository.findByEmail("target@example.com")).thenReturn(Optional.of(targetWithId));
        when(userRepository.findByIdWithPlaidItems(targetId)).thenReturn(Optional.of(targetUser));

        service.shareItem(itemId, userId, "target@example.com");

        assertThat(targetUser.getPlaidItems()).contains(item);
    }

    @Test
    void shouldThrowIllegalArgument_whenItemNotFound() {
        UUID itemId = UUID.randomUUID();
        User user = mock(User.class);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));
        when(plaidItemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.shareItem(itemId, userId, "t@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bank connection not found");
    }

    @Test
    void shouldThrowSecurityException_whenUserDoesNotOwnItem() {
        UUID itemId = UUID.randomUUID();
        PlaidItem item = mock(PlaidItem.class);
        User user = userWithItems(new ArrayList<>());
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));
        when(plaidItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.shareItem(itemId, userId, "t@example.com"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("do not have access");
    }

    @Test
    void shouldThrowIllegalArgument_whenTargetEmailNotFound() {
        UUID itemId = UUID.randomUUID();
        PlaidItem item = plaidItemWithId(itemId);
        User user = userWithItems(new ArrayList<>(List.of(item)));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));
        when(plaidItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.shareItem(itemId, userId, "ghost@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No user found with that email");
    }

    @Test
    void shouldNotDuplicateShare_whenTargetAlreadyOwnsItem() {
        UUID itemId = UUID.randomUUID();
        PlaidItem item = plaidItemWithId(itemId);
        User requestingUser = userWithItems(new ArrayList<>(List.of(item)));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(requestingUser));
        when(plaidItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        UUID targetId = UUID.randomUUID();
        User targetWithId = mock(User.class);
        when(targetWithId.getId()).thenReturn(targetId);
        when(userRepository.findByEmail("target@example.com")).thenReturn(Optional.of(targetWithId));

        User targetUser = userWithItems(new ArrayList<>(List.of(item)));
        when(userRepository.findByIdWithPlaidItems(targetId)).thenReturn(Optional.of(targetUser));

        service.shareItem(itemId, userId, "target@example.com");

        assertThat(targetUser.getPlaidItems()).hasSize(1);
    }

    private User userWithItems(List<PlaidItem> items) {
        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(items);
        return user;
    }

    private PlaidItem plaidItemWithId(UUID id) {
        PlaidItem item = mock(PlaidItem.class);
        when(item.getId()).thenReturn(id);
        return item;
    }
}
