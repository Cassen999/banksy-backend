package org.example.repository;

import org.example.entity.OAuthIdentity;
import org.example.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthIdentityRepositoryTest extends AbstractRepositoryTest {

    @Autowired private OAuthIdentityRepository oAuthIdentityRepository;
    @Autowired private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        User u = new User();
        u.setEmail("oauth@example.com");
        u.setFirstName("OAuth");
        u.setLastName("User");
        u.setUsername("oauthuser");
        user = userRepository.save(u);
    }

    @Test
    void shouldFindIdentity_whenProviderAndProviderUserIdMatch() {
        OAuthIdentity identity = new OAuthIdentity();
        identity.setUser(user);
        identity.setProvider("google");
        identity.setProviderUserId("google-sub-123");
        oAuthIdentityRepository.save(identity);

        Optional<OAuthIdentity> result =
                oAuthIdentityRepository.findByProviderAndProviderUserId("google", "google-sub-123");

        assertThat(result).isPresent();
        assertThat(result.get().getProvider()).isEqualTo("google");
    }

    @Test
    void shouldReturnEmpty_whenNoMatchingIdentity() {
        Optional<OAuthIdentity> result =
                oAuthIdentityRepository.findByProviderAndProviderUserId("github", "unknown-sub");

        assertThat(result).isEmpty();
    }
}
