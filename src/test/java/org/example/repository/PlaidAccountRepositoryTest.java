package org.example.repository;

import org.example.entity.PlaidAccount;
import org.example.entity.PlaidItem;
import org.example.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlaidAccountRepositoryTest extends AbstractRepositoryTest {

    @Autowired private PlaidAccountRepository plaidAccountRepository;
    @Autowired private PlaidItemRepository plaidItemRepository;
    @Autowired private UserRepository userRepository;

    private PlaidItem item;

    @BeforeEach
    void setUp() {
        User owner = new User();
        owner.setFirstName("Test");
        owner.setLastName("Owner");
        owner.setUsername("owner-" + UUID.randomUUID());
        owner.setEmail("owner-" + UUID.randomUUID() + "@example.com");
        owner = userRepository.save(owner);

        PlaidItem pi = new PlaidItem();
        pi.setAccessTokenEnc("enc-token");
        pi.setItemId("item-for-accounts-" + UUID.randomUUID());
        pi.setInstitutionId("ins-1");
        pi.setInstitutionName("Test Bank");
        pi.setOwner(owner);
        item = plaidItemRepository.save(pi);
    }

    @Test
    void shouldReturnTrue_whenPlaidAccountIdExists() {
        PlaidAccount account = new PlaidAccount();
        account.setPlaidItem(item);
        account.setPlaidAccountId("acct-exists-" + UUID.randomUUID());
        account.setName("Checking");
        account.setType("depository");
        plaidAccountRepository.save(account);

        assertThat(plaidAccountRepository.existsByPlaidAccountId(account.getPlaidAccountId())).isTrue();
    }

    @Test
    void shouldReturnFalse_whenPlaidAccountIdNotFound() {
        assertThat(plaidAccountRepository.existsByPlaidAccountId("acct-missing")).isFalse();
    }

    @Test
    void shouldFindAccountWithItem_whenAccountExists() {
        PlaidAccount account = savedAccount("acct-with-item-" + UUID.randomUUID(), false);

        Optional<PlaidAccount> result = plaidAccountRepository.findByIdWithItem(account.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getPlaidItem().getId()).isEqualTo(item.getId());
    }

    @Test
    void shouldReturnEmpty_whenAccountNotFound() {
        Optional<PlaidAccount> result = plaidAccountRepository.findByIdWithItem(UUID.randomUUID());
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnHiddenAccountIds_whenAccountsAreHidden() {
        PlaidAccount visible = savedAccount("acct-visible-" + UUID.randomUUID(), false);
        PlaidAccount hidden  = savedAccount("acct-hidden-"  + UUID.randomUUID(), true);

        Set<String> hiddenIds = plaidAccountRepository.findHiddenAccountIdsByItemId(item.getId());

        assertThat(hiddenIds).contains(hidden.getPlaidAccountId());
        assertThat(hiddenIds).doesNotContain(visible.getPlaidAccountId());
    }

    @Test
    void shouldReturnEmptySet_whenNoAccountsAreHidden() {
        savedAccount("acct-vis-" + UUID.randomUUID(), false);

        Set<String> hiddenIds = plaidAccountRepository.findHiddenAccountIdsByItemId(item.getId());

        assertThat(hiddenIds).isEmpty();
    }

    private PlaidAccount savedAccount(String plaidAccountId, boolean hidden) {
        PlaidAccount a = new PlaidAccount();
        a.setPlaidItem(item);
        a.setPlaidAccountId(plaidAccountId);
        a.setName("Account");
        a.setType("depository");
        a.setHidden(hidden);
        return plaidAccountRepository.save(a);
    }
}
