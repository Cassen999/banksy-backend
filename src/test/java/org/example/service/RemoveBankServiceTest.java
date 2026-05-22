package org.example.service;

import com.plaid.client.model.ItemRemoveRequest;
import com.plaid.client.model.ItemRemoveResponse;
import com.plaid.client.request.PlaidApi;
import okhttp3.ResponseBody;
import org.example.entity.PlaidAccount;
import org.example.entity.PlaidItem;
import org.example.entity.PlaidItemStatus;
import org.example.entity.User;
import org.example.repository.PlaidAccountRepository;
import org.example.repository.PlaidItemRepository;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RemoveBankServiceTest {

    @Mock private PlaidApi plaidClient;
    @Mock private EncryptionService encryptionService;
    @Mock private PlaidItemRepository plaidItemRepository;
    @Mock private PlaidAccountRepository plaidAccountRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;

    private RemoveBankService service;

    private final UUID userId      = UUID.randomUUID();
    private final UUID itemId      = UUID.randomUUID();
    private final UUID accountId   = UUID.randomUUID();

    private User user;
    private PlaidItem item;
    private PlaidAccount account;

    @BeforeEach
    void setUp() {
        service = new RemoveBankService(plaidClient, encryptionService,
                plaidItemRepository, plaidAccountRepository, userRepository, notificationService);

        user = new User();
        user.setEmail("test@example.com");

        item = new PlaidItem();
        item.setInstitutionName("Chase");
        item.setAccessTokenEnc("encrypted-token");
        item.setStatus(PlaidItemStatus.HEALTHY);

        account = new PlaidAccount();
        account.setMask("4242");
        account.setPlaidItem(item);
        account.setHidden(false);
    }

    // ─── hideAccount — happy paths ───────────────────────────────────────────

    @Test
    void hideAccount_shouldSetHiddenTrue_andNotifyLinkedUsers() {
        when(plaidAccountRepository.findByIdWithItem(accountId)).thenReturn(Optional.of(account));
        when(userRepository.findAllWithPlaidItem(any())).thenReturn(List.of(user));
        setUserId(user, userId);

        service.hideAccount(accountId, userId);

        assertThat(account.isHidden()).isTrue();
        verify(plaidAccountRepository).save(account);
        verify(notificationService).notifyAll(any(), eq("Removed bank account ending in 4242"));
    }

    @Test
    void hideAccount_shouldNotifyAllLinkedUsers_notJustRequester() {
        User otherUser = new User();
        setUserId(user, userId);
        setUserId(otherUser, UUID.randomUUID());

        when(plaidAccountRepository.findByIdWithItem(accountId)).thenReturn(Optional.of(account));
        when(userRepository.findAllWithPlaidItem(any())).thenReturn(List.of(user, otherUser));

        service.hideAccount(accountId, userId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationService).notifyAll(captor.capture(), any());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void hideAccount_shouldUseCorrectMask_inNotificationMessage() {
        account.setMask("9876");
        setUserId(user, userId);

        when(plaidAccountRepository.findByIdWithItem(accountId)).thenReturn(Optional.of(account));
        when(userRepository.findAllWithPlaidItem(any())).thenReturn(List.of(user));

        service.hideAccount(accountId, userId);

        verify(notificationService).notifyAll(any(), eq("Removed bank account ending in 9876"));
    }

    @Test
    void hideAccount_shouldUseFallback_whenMaskIsNull() {
        account.setMask(null);
        setUserId(user, userId);

        when(plaidAccountRepository.findByIdWithItem(accountId)).thenReturn(Optional.of(account));
        when(userRepository.findAllWithPlaidItem(any())).thenReturn(List.of(user));

        service.hideAccount(accountId, userId);

        verify(notificationService).notifyAll(any(), contains("Removed bank account ending in"));
    }

    // ─── hideAccount — error paths ────────────────────────────────────────────

    @Test
    void hideAccount_shouldThrowSecurityException_whenUserNotLinked() {
        User otherUser = new User();
        setUserId(otherUser, UUID.randomUUID());

        when(plaidAccountRepository.findByIdWithItem(accountId)).thenReturn(Optional.of(account));
        when(userRepository.findAllWithPlaidItem(any())).thenReturn(List.of(otherUser));

        assertThatThrownBy(() -> service.hideAccount(accountId, userId))
                .isInstanceOf(SecurityException.class);

        verify(notificationService, never()).notifyAll(any(), any());
        verify(plaidAccountRepository, never()).save(any());
    }

    @Test
    void hideAccount_shouldThrow_whenAccountNotFound() {
        when(plaidAccountRepository.findByIdWithItem(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.hideAccount(accountId, userId))
                .isInstanceOf(IllegalArgumentException.class);

        verify(notificationService, never()).notifyAll(any(), any());
    }

    @Test
    void hideAccount_shouldSendErrorNotification_whenSaveFails() {
        setUserId(user, userId);
        when(plaidAccountRepository.findByIdWithItem(accountId)).thenReturn(Optional.of(account));
        when(userRepository.findAllWithPlaidItem(any())).thenReturn(List.of(user));
        when(plaidAccountRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> service.hideAccount(accountId, userId))
                .isInstanceOf(RuntimeException.class);

        verify(notificationService).notifyAll(any(),
                contains("There was an error removing your account"));
    }

    // ─── removeItem — happy paths ─────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void removeItem_shouldCallPlaidAndDeleteItem_whenStatusIsHealthy() throws IOException {
        setUserId(user, userId);
        when(plaidItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(userRepository.findAllWithPlaidItem(itemId)).thenReturn(List.of(user));
        when(encryptionService.decrypt("encrypted-token")).thenReturn("raw-token");
        Call<ItemRemoveResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.success(mock(ItemRemoveResponse.class)));
        when(plaidClient.itemRemove(any(ItemRemoveRequest.class))).thenReturn(call);

        service.removeItem(itemId, userId);

        verify(plaidClient).itemRemove(any());
        verify(plaidItemRepository).delete(item);
        verify(notificationService).notifyAll(any(),
                contains("Removed Chase from Banksy"));
    }

    @Test
    void removeItem_shouldSkipPlaidCall_whenStatusIsInvalidToken() throws IOException {
        item.setStatus(PlaidItemStatus.INVALID_TOKEN);
        setUserId(user, userId);
        when(plaidItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(userRepository.findAllWithPlaidItem(itemId)).thenReturn(List.of(user));

        service.removeItem(itemId, userId);

        verify(plaidClient, never()).itemRemove(any());
        verify(plaidItemRepository).delete(item);
        verify(notificationService).notifyAll(any(), contains("Removed Chase from Banksy"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void removeItem_shouldCallPlaid_whenStatusIsNeedsReauth() throws IOException {
        item.setStatus(PlaidItemStatus.NEEDS_REAUTH);
        setUserId(user, userId);
        when(plaidItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(userRepository.findAllWithPlaidItem(itemId)).thenReturn(List.of(user));
        when(encryptionService.decrypt("encrypted-token")).thenReturn("raw-token");
        Call<ItemRemoveResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.success(mock(ItemRemoveResponse.class)));
        when(plaidClient.itemRemove(any())).thenReturn(call);

        service.removeItem(itemId, userId);

        verify(plaidClient).itemRemove(any());
        verify(plaidItemRepository).delete(item);
    }

    @Test
    void removeItem_shouldNotifyAllLinkedUsers_includingOwner() throws IOException {
        User owner = new User();
        User sharedUser = new User();
        setUserId(owner, userId);
        setUserId(sharedUser, UUID.randomUUID());
        item.setStatus(PlaidItemStatus.INVALID_TOKEN);

        when(plaidItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(userRepository.findAllWithPlaidItem(itemId)).thenReturn(List.of(owner, sharedUser));

        service.removeItem(itemId, userId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationService).notifyAll(captor.capture(), any());
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void removeItem_shouldIncludeInstitutionName_inSuccessNotification() throws IOException {
        item.setInstitutionName("Wells Fargo");
        item.setStatus(PlaidItemStatus.INVALID_TOKEN);
        setUserId(user, userId);

        when(plaidItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(userRepository.findAllWithPlaidItem(itemId)).thenReturn(List.of(user));

        service.removeItem(itemId, userId);

        verify(notificationService).notifyAll(any(), contains("Wells Fargo"));
    }

    // ─── removeItem — error paths ─────────────────────────────────────────────

    @Test
    void removeItem_shouldThrowSecurityException_whenUserNotLinked() {
        User otherUser = new User();
        setUserId(otherUser, UUID.randomUUID());
        when(plaidItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(userRepository.findAllWithPlaidItem(itemId)).thenReturn(List.of(otherUser));

        assertThatThrownBy(() -> service.removeItem(itemId, userId))
                .isInstanceOf(SecurityException.class);

        verify(notificationService, never()).notifyAll(any(), any());
        verify(plaidItemRepository, never()).delete(any());
    }

    @Test
    void removeItem_shouldThrow_whenItemNotFound() {
        when(plaidItemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeItem(itemId, userId))
                .isInstanceOf(IllegalArgumentException.class);

        verify(notificationService, never()).notifyAll(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void removeItem_shouldSendErrorNotification_whenPlaidCallFails() throws IOException {
        setUserId(user, userId);
        when(plaidItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(userRepository.findAllWithPlaidItem(itemId)).thenReturn(List.of(user));
        when(encryptionService.decrypt(any())).thenReturn("raw-token");
        Call<ItemRemoveResponse> call = mock(Call.class);
        when(call.execute()).thenReturn(
                Response.error(500, ResponseBody.create(null, "{}")));
        when(plaidClient.itemRemove(any())).thenReturn(call);

        assertThatThrownBy(() -> service.removeItem(itemId, userId))
                .isInstanceOf(RuntimeException.class);

        verify(notificationService).notifyAll(any(),
                contains("There was an error removing your account"));
        verify(plaidItemRepository, never()).delete(any());
    }

    @Test
    void removeItem_shouldSendErrorNotification_whenDeleteFails() throws IOException {
        item.setStatus(PlaidItemStatus.INVALID_TOKEN);
        setUserId(user, userId);
        when(plaidItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(userRepository.findAllWithPlaidItem(itemId)).thenReturn(List.of(user));
        doThrow(new RuntimeException("DB error")).when(plaidItemRepository).delete(any());

        assertThatThrownBy(() -> service.removeItem(itemId, userId))
                .isInstanceOf(RuntimeException.class);

        verify(notificationService).notifyAll(any(),
                contains("There was an error removing your account"));
    }

    // ─── helper ───────────────────────────────────────────────────────────────

    private void setUserId(User u, UUID id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(u, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
