package org.example.repository;

import org.example.entity.User;
import org.example.entity.UserAccountName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserAccountNameRepositoryTest extends AbstractRepositoryTest {

    @Autowired private UserAccountNameRepository accountNameRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void shouldFindByUserIdAndPlaidAccountId_whenEntryExists() {
        User user = savedUser();
        savedAccountName(user, "acct-123", "My Checking");

        Optional<UserAccountName> result =
                accountNameRepository.findByUserIdAndPlaidAccountId(user.getId(), "acct-123");

        assertThat(result).isPresent();
        assertThat(result.get().getCustomName()).isEqualTo("My Checking");
    }

    @Test
    void shouldReturnEmpty_whenNoEntryForUserAndAccount() {
        User user = savedUser();

        Optional<UserAccountName> result =
                accountNameRepository.findByUserIdAndPlaidAccountId(user.getId(), "acct-nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldNotReturnEntry_forDifferentUser() {
        User user1 = savedUser();
        User user2 = savedUser();
        savedAccountName(user1, "acct-123", "User1 Account");

        Optional<UserAccountName> result =
                accountNameRepository.findByUserIdAndPlaidAccountId(user2.getId(), "acct-123");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnAllEntries_forTheSpecifiedUser() {
        User user = savedUser();
        savedAccountName(user, "acct-abc", "Checking");
        savedAccountName(user, "acct-def", "Savings");

        List<UserAccountName> result = accountNameRepository.findAllByUserId(user.getId());

        assertThat(result).hasSize(2);
        assertThat(result).extracting(UserAccountName::getPlaidAccountId)
                .containsExactlyInAnyOrder("acct-abc", "acct-def");
    }

    @Test
    void shouldNotReturnEntries_forOtherUsers() {
        User user1 = savedUser();
        User user2 = savedUser();
        savedAccountName(user1, "acct-user1", "User1 Account");
        savedAccountName(user2, "acct-user2", "User2 Account");

        List<UserAccountName> result = accountNameRepository.findAllByUserId(user1.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPlaidAccountId()).isEqualTo("acct-user1");
    }

    @Test
    void shouldReturnEmptyList_whenUserHasNoCustomNames() {
        User user = savedUser();

        List<UserAccountName> result = accountNameRepository.findAllByUserId(user.getId());

        assertThat(result).isEmpty();
    }

    private User savedUser() {
        User u = new User();
        u.setFirstName("Test");
        u.setLastName("User");
        u.setUsername("user-" + UUID.randomUUID());
        u.setEmail("user-" + UUID.randomUUID() + "@example.com");
        return userRepository.save(u);
    }

    private void savedAccountName(User user, String plaidAccountId, String customName) {
        UserAccountName entry = new UserAccountName();
        entry.setUser(user);
        entry.setPlaidAccountId(plaidAccountId);
        entry.setCustomName(customName);
        accountNameRepository.save(entry);
    }
}
