package org.example.repository;

import org.example.entity.User;
import org.example.entity.UserExcludedAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserExcludedAccountRepositoryTest extends AbstractRepositoryTest {

    @Autowired private UserExcludedAccountRepository excludedAccountRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void shouldReturnPlaidAccountIds_forTheSpecifiedUser() {
        User user = savedUser();
        savedExcludedAccount(user, "acct-abc123");
        savedExcludedAccount(user, "acct-def456");

        Set<String> result = excludedAccountRepository.findPlaidAccountIdsByUserId(user.getId());

        assertThat(result).containsExactlyInAnyOrder("acct-abc123", "acct-def456");
    }

    @Test
    void shouldNotReturnAccountIds_forOtherUsers() {
        User user1 = savedUser();
        User user2 = savedUser();
        savedExcludedAccount(user1, "acct-user1");
        savedExcludedAccount(user2, "acct-user2");

        Set<String> result = excludedAccountRepository.findPlaidAccountIdsByUserId(user1.getId());

        assertThat(result).containsOnly("acct-user1");
        assertThat(result).doesNotContain("acct-user2");
    }

    @Test
    void shouldReturnEmptySet_whenUserHasNoExcludedAccounts() {
        User user = savedUser();

        Set<String> result = excludedAccountRepository.findPlaidAccountIdsByUserId(user.getId());

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

    private void savedExcludedAccount(User user, String plaidAccountId) {
        UserExcludedAccount ea = new UserExcludedAccount();
        ea.setUser(user);
        ea.setPlaidAccountId(plaidAccountId);
        excludedAccountRepository.save(ea);
    }
}
