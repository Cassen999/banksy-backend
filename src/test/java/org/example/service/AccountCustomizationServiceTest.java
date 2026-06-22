package org.example.service;

import org.example.entity.PlaidAccount;
import org.example.entity.PlaidItem;
import org.example.entity.User;
import org.example.entity.UserAccountName;
import org.example.repository.PlaidAccountRepository;
import org.example.repository.UserAccountNameRepository;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountCustomizationServiceTest {

    @Mock private UserAccountNameRepository accountNameRepository;
    @Mock private PlaidAccountRepository plaidAccountRepository;
    @Mock private UserRepository userRepository;

    private AccountCustomizationService service;
    private final UUID userId = UUID.randomUUID();
    private final String plaidAccountId = "acct-abc123";

    @BeforeEach
    void setUp() {
        service = new AccountCustomizationService(accountNameRepository, plaidAccountRepository, userRepository);
    }

    // ─── setCustomName ────────────────────────────────────────────────────────

    @Test
    void setCustomName_shouldCreateNewEntry_whenNoneExists() {
        UUID itemId = UUID.randomUUID();
        PlaidItem item = mock(PlaidItem.class);
        when(item.getId()).thenReturn(itemId);

        PlaidAccount account = mock(PlaidAccount.class);
        when(account.getPlaidItem()).thenReturn(item);
        when(plaidAccountRepository.findByPlaidAccountIdWithItem(plaidAccountId))
                .thenReturn(Optional.of(account));

        PlaidItem linkedItem = mock(PlaidItem.class);
        when(linkedItem.getId()).thenReturn(itemId);
        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(List.of(linkedItem));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        when(accountNameRepository.findByUserIdAndPlaidAccountId(userId, plaidAccountId))
                .thenReturn(Optional.empty());

        service.setCustomName(userId, plaidAccountId, "My Checking");

        ArgumentCaptor<UserAccountName> captor = ArgumentCaptor.forClass(UserAccountName.class);
        verify(accountNameRepository).save(captor.capture());
        assertThat(captor.getValue().getCustomName()).isEqualTo("My Checking");
        assertThat(captor.getValue().getPlaidAccountId()).isEqualTo(plaidAccountId);
    }

    @Test
    void setCustomName_shouldUpdateExistingEntry_whenOneExists() {
        UUID itemId = UUID.randomUUID();
        PlaidItem item = mock(PlaidItem.class);
        when(item.getId()).thenReturn(itemId);

        PlaidAccount account = mock(PlaidAccount.class);
        when(account.getPlaidItem()).thenReturn(item);
        when(plaidAccountRepository.findByPlaidAccountIdWithItem(plaidAccountId))
                .thenReturn(Optional.of(account));

        PlaidItem linkedItem = mock(PlaidItem.class);
        when(linkedItem.getId()).thenReturn(itemId);
        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(List.of(linkedItem));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        UserAccountName existing = new UserAccountName();
        existing.setCustomName("Old Name");
        when(accountNameRepository.findByUserIdAndPlaidAccountId(userId, plaidAccountId))
                .thenReturn(Optional.of(existing));

        service.setCustomName(userId, plaidAccountId, "New Name");

        ArgumentCaptor<UserAccountName> captor = ArgumentCaptor.forClass(UserAccountName.class);
        verify(accountNameRepository).save(captor.capture());
        assertThat(captor.getValue().getCustomName()).isEqualTo("New Name");
    }

    @Test
    void setCustomName_shouldThrowNoSuchElementException_whenAccountNotFound() {
        when(plaidAccountRepository.findByPlaidAccountIdWithItem(plaidAccountId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setCustomName(userId, plaidAccountId, "Name"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void setCustomName_shouldThrowSecurityException_whenUserNotLinked() {
        UUID itemId = UUID.randomUUID();
        PlaidItem item = mock(PlaidItem.class);
        when(item.getId()).thenReturn(itemId);

        PlaidAccount account = mock(PlaidAccount.class);
        when(account.getPlaidItem()).thenReturn(item);
        when(plaidAccountRepository.findByPlaidAccountIdWithItem(plaidAccountId))
                .thenReturn(Optional.of(account));

        PlaidItem otherItem = mock(PlaidItem.class);
        when(otherItem.getId()).thenReturn(UUID.randomUUID());
        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(List.of(otherItem));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.setCustomName(userId, plaidAccountId, "Name"))
                .isInstanceOf(SecurityException.class);
    }

    // ─── deleteCustomName ─────────────────────────────────────────────────────

    @Test
    void deleteCustomName_shouldDeleteEntry_whenOneExists() {
        UUID itemId = UUID.randomUUID();
        PlaidItem item = mock(PlaidItem.class);
        when(item.getId()).thenReturn(itemId);

        PlaidAccount account = mock(PlaidAccount.class);
        when(account.getPlaidItem()).thenReturn(item);
        when(plaidAccountRepository.findByPlaidAccountIdWithItem(plaidAccountId))
                .thenReturn(Optional.of(account));

        PlaidItem linkedItem = mock(PlaidItem.class);
        when(linkedItem.getId()).thenReturn(itemId);
        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(List.of(linkedItem));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        UserAccountName existing = new UserAccountName();
        when(accountNameRepository.findByUserIdAndPlaidAccountId(userId, plaidAccountId))
                .thenReturn(Optional.of(existing));

        service.deleteCustomName(userId, plaidAccountId);

        verify(accountNameRepository).delete(existing);
    }

    @Test
    void deleteCustomName_shouldBeNoOp_whenNoEntryExists() {
        UUID itemId = UUID.randomUUID();
        PlaidItem item = mock(PlaidItem.class);
        when(item.getId()).thenReturn(itemId);

        PlaidAccount account = mock(PlaidAccount.class);
        when(account.getPlaidItem()).thenReturn(item);
        when(plaidAccountRepository.findByPlaidAccountIdWithItem(plaidAccountId))
                .thenReturn(Optional.of(account));

        PlaidItem linkedItem = mock(PlaidItem.class);
        when(linkedItem.getId()).thenReturn(itemId);
        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(List.of(linkedItem));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        when(accountNameRepository.findByUserIdAndPlaidAccountId(userId, plaidAccountId))
                .thenReturn(Optional.empty());

        service.deleteCustomName(userId, plaidAccountId);

        verify(accountNameRepository, never()).delete(any(UserAccountName.class));
    }

    @Test
    void deleteCustomName_shouldThrowNoSuchElementException_whenAccountNotFound() {
        when(plaidAccountRepository.findByPlaidAccountIdWithItem(plaidAccountId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteCustomName(userId, plaidAccountId))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void deleteCustomName_shouldThrowSecurityException_whenUserNotLinked() {
        UUID itemId = UUID.randomUUID();
        PlaidItem item = mock(PlaidItem.class);
        when(item.getId()).thenReturn(itemId);

        PlaidAccount account = mock(PlaidAccount.class);
        when(account.getPlaidItem()).thenReturn(item);
        when(plaidAccountRepository.findByPlaidAccountIdWithItem(plaidAccountId))
                .thenReturn(Optional.of(account));

        PlaidItem otherItem = mock(PlaidItem.class);
        when(otherItem.getId()).thenReturn(UUID.randomUUID());
        User user = mock(User.class);
        when(user.getPlaidItems()).thenReturn(List.of(otherItem));
        when(userRepository.findByIdWithPlaidItems(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.deleteCustomName(userId, plaidAccountId))
                .isInstanceOf(SecurityException.class);
    }
}
