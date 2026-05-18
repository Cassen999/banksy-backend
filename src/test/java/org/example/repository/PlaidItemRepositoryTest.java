package org.example.repository;

import org.example.entity.PlaidItem;
import org.example.entity.PlaidItemStatus;
import org.example.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlaidItemRepositoryTest extends AbstractRepositoryTest {

    @Autowired private PlaidItemRepository plaidItemRepository;
    @Autowired private UserRepository userRepository;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setFirstName("Test");
        owner.setLastName("Owner");
        owner.setUsername("owner-" + UUID.randomUUID());
        owner.setEmail("owner-" + UUID.randomUUID() + "@example.com");
        owner = userRepository.save(owner);
    }

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

    @Test
    void shouldPersistOwnerAndStatus_whenItemIsSaved() {
        PlaidItem item = savedItem("item-with-owner");

        PlaidItem found = plaidItemRepository.findByItemId("item-with-owner").orElseThrow();

        assertThat(found.getOwner().getId()).isEqualTo(owner.getId());
        assertThat(found.getStatus()).isEqualTo(PlaidItemStatus.HEALTHY);
    }

    private PlaidItem savedItem(String itemId) {
        PlaidItem item = new PlaidItem();
        item.setAccessTokenEnc("enc-token");
        item.setItemId(itemId);
        item.setInstitutionId("ins-1");
        item.setInstitutionName("Test Bank");
        item.setOwner(owner);
        return plaidItemRepository.save(item);
    }
}
