package org.example.repository;

import org.example.entity.PlaidItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PlaidItemRepositoryTest extends AbstractRepositoryTest {

    @Autowired private PlaidItemRepository plaidItemRepository;

    @Test
    void shouldFindItem_whenItemIdExists() {
        PlaidItem item = savedItem("item-find-me");

        Optional<PlaidItem> result = plaidItemRepository.findByItemId("item-find-me");

        assertThat(result).isPresent();
        assertThat(result.get().getItemId()).isEqualTo("item-find-me");
    }

    @Test
    void shouldReturnEmpty_whenItemIdNotFound() {
        Optional<PlaidItem> result = plaidItemRepository.findByItemId("does-not-exist");

        assertThat(result).isEmpty();
    }

    private PlaidItem savedItem(String itemId) {
        PlaidItem item = new PlaidItem();
        item.setAccessTokenEnc("enc-token");
        item.setItemId(itemId);
        item.setInstitutionId("ins-1");
        item.setInstitutionName("Test Bank");
        return plaidItemRepository.save(item);
    }
}
