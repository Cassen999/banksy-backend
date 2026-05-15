package org.example.repository;

import org.example.entity.PlaidItem;
import org.example.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryTest extends AbstractRepositoryTest {

    @Autowired private UserRepository userRepository;
    @Autowired private PlaidItemRepository plaidItemRepository;

    @Test
    void shouldFindUser_whenEmailExists() {
        User user = savedUser("find@example.com", "findme");

        Optional<User> result = userRepository.findByEmail("find@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("find@example.com");
    }

    @Test
    void shouldReturnEmpty_whenEmailNotFound() {
        Optional<User> result = userRepository.findByEmail("nobody@example.com");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnUserWithPlaidItems_whenUserHasItems() {
        User user = savedUser("withitems@example.com", "withitems");

        PlaidItem item = new PlaidItem();
        item.setAccessTokenEnc("enc-token");
        item.setItemId("item-abc");
        item.setInstitutionId("ins-1");
        item.setInstitutionName("Test Bank");
        plaidItemRepository.save(item);

        user.getPlaidItems().add(item);
        userRepository.save(user);

        Optional<User> result = userRepository.findByIdWithPlaidItems(user.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getPlaidItems()).hasSize(1);
        assertThat(result.get().getPlaidItems().get(0).getItemId()).isEqualTo("item-abc");
    }

    @Test
    void shouldReturnUserWithEmptyList_whenUserHasNoItems() {
        User user = savedUser("noitems@example.com", "noitems");

        Optional<User> result = userRepository.findByIdWithPlaidItems(user.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getPlaidItems()).isEmpty();
    }

    private User savedUser(String email, String username) {
        User user = new User();
        user.setEmail(email);
        user.setFirstName("Test");
        user.setLastName("User");
        user.setUsername(username);
        return userRepository.save(user);
    }
}
