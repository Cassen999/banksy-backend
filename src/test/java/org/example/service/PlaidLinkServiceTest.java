package org.example.service;

import com.plaid.client.model.*;
import com.plaid.client.request.PlaidApi;
import okhttp3.ResponseBody;
import org.example.entity.PlaidAccount;
import org.example.entity.PlaidItem;
import org.example.entity.PlaidItemStatus;
import org.example.entity.User;
import org.example.model.RelinkSignal;
import org.example.plaid.PlaidTokenError;
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

import org.mockito.ArgumentCaptor;

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

    // --- createLinkToken ---

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

    // --- linkTokenRefresh ---

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnRefreshLinkToken_whenOwnerRequestsIt() throws IOException {
        UUID itemId = UUID.randomUUID();
        PlaidItem item = itemOwnedBy(userId);
        when(item.getAccessTokenEnc()).thenReturn("enc-access-token");
        when(plaidItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(encryptionService.decrypt("enc-access-token")).thenReturn("decrypted-token");

        LinkTokenCreateResponse linkBody = mock(LinkTokenCreateResponse.class);
        when(linkBody.getLinkToken()).thenReturn("refresh-link-token");
        Call<LinkTokenCreateResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.success(linkBody));
        when(plaidClient.linkTokenCreate(any())).thenReturn(call);

        String result = service.linkTokenRefresh(userId, itemId);

        assertThat(result).isEqualTo("refresh-link-token");
    }

    @Test
    void shouldThrowSecurityException_whenNonOwnerRequestsRefreshToken() {
        UUID itemId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        PlaidItem item = itemOwnedBy(otherUserId);
        when(plaidItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.linkTokenRefresh(userId, itemId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("do not have access");
    }

    @Test
    void shouldThrowIllegalArgument_whenItemNotFoundForRefresh() {
        when(plaidItemRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.linkTokenRefresh(userId, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bank connection not found");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldThrowRuntimeException_whenPlaidRefreshLinkTokenCallFails() throws IOException {
        UUID itemId = UUID.randomUUID();
        PlaidItem item = itemOwnedBy(userId);
        when(item.getAccessTokenEnc()).thenReturn("enc-token");
        when(plaidItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(encryptionService.decrypt("enc-token")).thenReturn("access-token");

        Call<LinkTokenCreateResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.error(500, okhttp3.ResponseBody.create(null, "error")));
        when(plaidClient.linkTokenCreate(any())).thenReturn(call);

        assertThatThrownBy(() -> service.linkTokenRefresh(userId, itemId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to create Plaid refresh link token");
    }

    // --- fullRelinkToken ---

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnFullRelinkToken_whenOwnerRequestsIt() throws IOException {
        UUID itemId = UUID.randomUUID();
        PlaidItem item = itemOwnedBy(userId);
        when(plaidItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        LinkTokenCreateResponse linkBody = mock(LinkTokenCreateResponse.class);
        when(linkBody.getLinkToken()).thenReturn("full-relink-token");
        Call<LinkTokenCreateResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.success(linkBody));
        when(plaidClient.linkTokenCreate(any())).thenReturn(call);

        String result = service.fullRelinkToken(userId, itemId);

        assertThat(result).isEqualTo("full-relink-token");
        verifyNoInteractions(encryptionService);
    }

    @Test
    void shouldThrowSecurityException_whenNonOwnerRequestsFullRelinkToken() {
        UUID itemId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        PlaidItem item = itemOwnedBy(otherUserId);
        when(plaidItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.fullRelinkToken(userId, itemId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("do not have access");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldThrowRuntimeException_whenPlaidFullRelinkTokenCallFails() throws IOException {
        UUID itemId = UUID.randomUUID();
        PlaidItem item = itemOwnedBy(userId);
        when(plaidItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        Call<LinkTokenCreateResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.error(500, okhttp3.ResponseBody.create(null, "error")));
        when(plaidClient.linkTokenCreate(any())).thenReturn(call);

        assertThatThrownBy(() -> service.fullRelinkToken(userId, itemId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to create Plaid full relink token");
    }

    // --- getRelinkStatus ---

    @Test
    void shouldReturnSignalsForNonHealthyItems_andSkipHealthyItems() {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(userId);

        PlaidItem healthyItem = mock(PlaidItem.class);
        when(healthyItem.getStatus()).thenReturn(PlaidItemStatus.HEALTHY);

        PlaidItem needsReauthItem = mock(PlaidItem.class);
        when(needsReauthItem.getStatus()).thenReturn(PlaidItemStatus.NEEDS_REAUTH);
        when(needsReauthItem.getOwner()).thenReturn(owner);
        when(needsReauthItem.getId()).thenReturn(UUID.randomUUID());
        when(needsReauthItem.getInstitutionName()).thenReturn("Chase");

        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(List.of(healthyItem, needsReauthItem));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        List<RelinkSignal> result = service.getRelinkStatus(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).errorType()).isEqualTo(PlaidTokenError.LOGIN_REQUIRED);
        assertThat(result.get(0).canRelink()).isTrue();
    }

    @Test
    void shouldReturnEmptyList_whenAllItemsAreHealthy() {
        PlaidItem item = mock(PlaidItem.class);
        when(item.getStatus()).thenReturn(PlaidItemStatus.HEALTHY);

        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(List.of(item));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        List<RelinkSignal> result = service.getRelinkStatus(userId);

        assertThat(result).isEmpty();
    }

    // --- replaceExpiredItem ---

    @Test
    void shouldMigrateAllUsersFromOldItemToNewItem_andDeleteOldItem() {
        UUID oldItemId = UUID.randomUUID();
        UUID newItemId = UUID.randomUUID();
        UUID sharedUserId = UUID.randomUUID();

        PlaidItem oldItem = mock(PlaidItem.class);
        when(oldItem.getId()).thenReturn(oldItemId);
        PlaidItem newItem = mock(PlaidItem.class);

        when(plaidItemRepository.findById(oldItemId)).thenReturn(Optional.of(oldItem));
        when(plaidItemRepository.findById(newItemId)).thenReturn(Optional.of(newItem));

        User sharedUser = mock(User.class);
        when(sharedUser.getId()).thenReturn(sharedUserId);
        when(userRepository.findAllWithPlaidItem(oldItemId)).thenReturn(List.of(sharedUser));

        List<PlaidItem> mutableItems = new ArrayList<>(List.of(oldItem));
        User loadedUser = mock(User.class);
        when(loadedUser.getPlaidItems()).thenReturn(mutableItems);
        when(userRepository.findByIdWithPlaidItems(sharedUserId)).thenReturn(Optional.of(loadedUser));

        service.replaceExpiredItem(oldItemId, newItemId);

        assertThat(mutableItems).doesNotContain(oldItem);
        assertThat(mutableItems).contains(newItem);
        verify(plaidItemRepository).delete(oldItem);
    }

    @Test
    void shouldNotDuplicateNewItem_whenUserAlreadyHasNewItem() {
        UUID oldItemId = UUID.randomUUID();
        UUID newItemId = UUID.randomUUID();
        UUID sharedUserId = UUID.randomUUID();

        PlaidItem oldItem = mock(PlaidItem.class);
        when(oldItem.getId()).thenReturn(oldItemId);
        PlaidItem newItem = mock(PlaidItem.class);
        when(newItem.getId()).thenReturn(newItemId);

        when(plaidItemRepository.findById(oldItemId)).thenReturn(Optional.of(oldItem));
        when(plaidItemRepository.findById(newItemId)).thenReturn(Optional.of(newItem));

        User sharedUser = mock(User.class);
        when(sharedUser.getId()).thenReturn(sharedUserId);
        when(userRepository.findAllWithPlaidItem(oldItemId)).thenReturn(List.of(sharedUser));

        List<PlaidItem> mutableItems = new ArrayList<>(List.of(oldItem, newItem));
        User loadedUser = mock(User.class);
        when(loadedUser.getPlaidItems()).thenReturn(mutableItems);
        when(userRepository.findByIdWithPlaidItems(sharedUserId)).thenReturn(Optional.of(loadedUser));

        service.replaceExpiredItem(oldItemId, newItemId);

        assertThat(mutableItems).doesNotContain(oldItem);
        assertThat(mutableItems).containsExactly(newItem);
    }

    // --- exchangeAndStore ---

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
        when(plaidItemRepository.save(any())).thenReturn(new PlaidItem());

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
    void shouldResetStatusAndSave_whenExchangingExistingItem() throws IOException {
        UUID existingItemId = UUID.randomUUID();
        PlaidItem existingItem = mock(PlaidItem.class);
        when(existingItem.getId()).thenReturn(existingItemId);
        when(existingItem.getAccessTokenEnc()).thenReturn("enc-stored-token");

        User user = userWithItems(new ArrayList<>(List.of(existingItem)));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        ItemPublicTokenExchangeResponse exchangeBody = mock(ItemPublicTokenExchangeResponse.class);
        when(exchangeBody.getAccessToken()).thenReturn("access-token");
        when(exchangeBody.getItemId()).thenReturn("item-id-existing");
        Call<ItemPublicTokenExchangeResponse> exchangeCall = mock(Call.class);
        when(exchangeCall.execute()).thenReturn(Response.success(exchangeBody));
        when(plaidClient.itemPublicTokenExchange(any())).thenReturn(exchangeCall);
        when(plaidItemRepository.findByItemId("item-id-existing")).thenReturn(Optional.of(existingItem));
        when(encryptionService.decrypt("enc-stored-token")).thenReturn("access-token");
        when(plaidItemRepository.save(existingItem)).thenReturn(existingItem);

        UUID result = service.exchangeAndStore("public-token", "ins-1", "Test Bank", userId);

        verify(existingItem).setStatus(PlaidItemStatus.HEALTHY);
        verify(plaidItemRepository).save(existingItem);
        assertThat(result).isEqualTo(existingItemId);
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

    // --- shareItem ---

    @Test
    void shouldShareItem_whenRequestingUserOwnsIt() {
        UUID itemId = UUID.randomUUID();
        PlaidItem item = plaidItemWithId(itemId);
        User requestingUser = userWithItems(new ArrayList<>(List.of(item)));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(requestingUser));
        when(plaidItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        UUID targetId = UUID.randomUUID();
        User targetWithId = mock(User.class);
        when(targetWithId.getId()).thenReturn(targetId);
        when(userRepository.findByEmail("target@example.com")).thenReturn(Optional.of(targetWithId));

        User targetUser = userWithItems(new ArrayList<>());
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

    @Test
    @SuppressWarnings("unchecked")
    void shouldReEncryptToken_whenRelinkedTokenIsDifferent() throws IOException {
        UUID existingItemId = UUID.randomUUID();
        PlaidItem existingItem = mock(PlaidItem.class);
        when(existingItem.getId()).thenReturn(existingItemId);
        when(existingItem.getAccessTokenEnc()).thenReturn("enc-old-token");

        User user = userWithItems(new ArrayList<>(List.of(existingItem)));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        ItemPublicTokenExchangeResponse exchangeBody = mock(ItemPublicTokenExchangeResponse.class);
        when(exchangeBody.getAccessToken()).thenReturn("new-access-token");
        when(exchangeBody.getItemId()).thenReturn("item-id-existing");
        Call<ItemPublicTokenExchangeResponse> exchangeCall = mock(Call.class);
        when(exchangeCall.execute()).thenReturn(Response.success(exchangeBody));
        when(plaidClient.itemPublicTokenExchange(any())).thenReturn(exchangeCall);
        when(plaidItemRepository.findByItemId("item-id-existing")).thenReturn(Optional.of(existingItem));
        when(encryptionService.decrypt("enc-old-token")).thenReturn("old-access-token");
        when(encryptionService.encrypt("new-access-token")).thenReturn("enc-new-token");
        when(plaidItemRepository.save(existingItem)).thenReturn(existingItem);

        service.exchangeAndStore("public-token", "ins-1", "Test Bank", userId);

        verify(existingItem).setAccessTokenEnc("enc-new-token");
        verify(existingItem).setStatus(PlaidItemStatus.HEALTHY);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAddExistingItemToUser_whenUserDoesNotAlreadyHaveIt() throws IOException {
        UUID existingItemId = UUID.randomUUID();
        PlaidItem existingItem = mock(PlaidItem.class);
        when(existingItem.getId()).thenReturn(existingItemId);
        when(existingItem.getAccessTokenEnc()).thenReturn("enc-token");

        List<PlaidItem> mutableUserItems = new ArrayList<>();
        User user = userWithItems(mutableUserItems);
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        ItemPublicTokenExchangeResponse exchangeBody = mock(ItemPublicTokenExchangeResponse.class);
        when(exchangeBody.getAccessToken()).thenReturn("access-token");
        when(exchangeBody.getItemId()).thenReturn("item-id-existing");
        Call<ItemPublicTokenExchangeResponse> exchangeCall = mock(Call.class);
        when(exchangeCall.execute()).thenReturn(Response.success(exchangeBody));
        when(plaidClient.itemPublicTokenExchange(any())).thenReturn(exchangeCall);
        when(plaidItemRepository.findByItemId("item-id-existing")).thenReturn(Optional.of(existingItem));
        when(encryptionService.decrypt("enc-token")).thenReturn("access-token");
        when(plaidItemRepository.save(existingItem)).thenReturn(existingItem);

        service.exchangeAndStore("public-token", "ins-1", "Test Bank", userId);

        assertThat(mutableUserItems).contains(existingItem);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSaveAccountWithNullType_whenTypeIsAbsentAndSubtypeIsPresent() throws IOException {
        User user = userWithItems(new ArrayList<>());
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        ItemPublicTokenExchangeResponse exchangeBody = mock(ItemPublicTokenExchangeResponse.class);
        when(exchangeBody.getAccessToken()).thenReturn("access-token");
        when(exchangeBody.getItemId()).thenReturn("item-id-null-type");
        Call<ItemPublicTokenExchangeResponse> exchangeCall = mock(Call.class);
        when(exchangeCall.execute()).thenReturn(Response.success(exchangeBody));
        when(plaidClient.itemPublicTokenExchange(any())).thenReturn(exchangeCall);
        when(plaidItemRepository.findByItemId("item-id-null-type")).thenReturn(Optional.empty());
        when(encryptionService.encrypt("access-token")).thenReturn("enc-token");
        when(plaidItemRepository.save(any())).thenReturn(new PlaidItem());

        AccountBase account = mock(AccountBase.class);
        when(account.getAccountId()).thenReturn("acct-null-type");
        when(account.getType()).thenReturn(null);
        AccountSubtype subtype = mock(AccountSubtype.class);
        when(subtype.getValue()).thenReturn("checking");
        when(account.getSubtype()).thenReturn(subtype);
        AccountsGetResponse accountsBody = mock(AccountsGetResponse.class);
        when(accountsBody.getAccounts()).thenReturn(List.of(account));
        Call<AccountsGetResponse> accountsCall = mock(Call.class);
        when(accountsCall.execute()).thenReturn(Response.success(accountsBody));
        when(plaidClient.accountsGet(any())).thenReturn(accountsCall);
        when(plaidAccountRepository.existsByPlaidAccountId("acct-null-type")).thenReturn(false);

        service.exchangeAndStore("public-token", "ins-1", "Test Bank", userId);

        ArgumentCaptor<PlaidAccount> captor = ArgumentCaptor.forClass(PlaidAccount.class);
        verify(plaidAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isNull();
        assertThat(captor.getValue().getSubtype()).isEqualTo("checking");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldProceedWithoutAccounts_whenAccountsFetchFails() throws IOException {
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
        when(plaidItemRepository.save(any())).thenReturn(new PlaidItem());

        Call<AccountsGetResponse> accountsCall = mock(Call.class);
        when(accountsCall.execute()).thenReturn(Response.error(500, okhttp3.ResponseBody.create(null, "error")));
        when(plaidClient.accountsGet(any())).thenReturn(accountsCall);

        service.exchangeAndStore("public-token", "ins-1", "Test Bank", userId);

        verify(plaidAccountRepository, never()).save(any());
    }

    // --- helpers ---

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

    private PlaidItem itemOwnedBy(UUID ownerId) {
        User owner = mock(User.class);
        when(owner.getId()).thenReturn(ownerId);

        PlaidItem item = mock(PlaidItem.class);
        when(item.getOwner()).thenReturn(owner);
        return item;
    }
}
