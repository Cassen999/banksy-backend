package org.example.repository;

import org.example.entity.PlaidAccount;
import org.example.entity.PlaidItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class PlaidAccountRepositoryTest extends AbstractRepositoryTest {

    @Autowired private PlaidAccountRepository plaidAccountRepository;
    @Autowired private PlaidItemRepository plaidItemRepository;

    private PlaidItem item;

    @BeforeEach
    void setUp() {
        PlaidItem pi = new PlaidItem();
        pi.setAccessTokenEnc("enc-token");
        pi.setItemId("item-for-accounts");
        pi.setInstitutionId("ins-1");
        pi.setInstitutionName("Test Bank");
        item = plaidItemRepository.save(pi);
    }

    @Test
    void shouldReturnTrue_whenPlaidAccountIdExists() {
        PlaidAccount account = new PlaidAccount();
        account.setPlaidItem(item);
        account.setPlaidAccountId("acct-exists");
        account.setName("Checking");
        account.setType("depository");
        plaidAccountRepository.save(account);

        assertThat(plaidAccountRepository.existsByPlaidAccountId("acct-exists")).isTrue();
    }

    @Test
    void shouldReturnFalse_whenPlaidAccountIdNotFound() {
        assertThat(plaidAccountRepository.existsByPlaidAccountId("acct-missing")).isFalse();
    }
}
