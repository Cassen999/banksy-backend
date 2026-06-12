package org.example.repository;

import org.example.entity.User;
import org.example.entity.UserRejectedCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserRejectedCategoryRepositoryTest extends AbstractRepositoryTest {

    @Autowired private UserRejectedCategoryRepository rejectedCategoryRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void shouldReturnCategories_forTheSpecifiedUser() {
        User user = savedUser();
        savedRejectedCategory(user, "PERSONAL_CARE_GYMS_AND_FITNESS_CENTERS");
        savedRejectedCategory(user, "ENTERTAINMENT");

        Set<String> result = rejectedCategoryRepository.findCategoriesByUserId(user.getId());

        assertThat(result).containsExactlyInAnyOrder("PERSONAL_CARE_GYMS_AND_FITNESS_CENTERS", "ENTERTAINMENT");
    }

    @Test
    void shouldNotReturnCategories_forOtherUsers() {
        User user1 = savedUser();
        User user2 = savedUser();
        savedRejectedCategory(user1, "FOOD_AND_DRINK");
        savedRejectedCategory(user2, "MEDICAL");

        Set<String> result = rejectedCategoryRepository.findCategoriesByUserId(user1.getId());

        assertThat(result).containsOnly("FOOD_AND_DRINK");
        assertThat(result).doesNotContain("MEDICAL");
    }

    @Test
    void shouldReturnEmptySet_whenUserHasNoRejectedCategories() {
        User user = savedUser();

        Set<String> result = rejectedCategoryRepository.findCategoriesByUserId(user.getId());

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

    private void savedRejectedCategory(User user, String category) {
        UserRejectedCategory rc = new UserRejectedCategory();
        rc.setUser(user);
        rc.setCategory(category);
        rejectedCategoryRepository.save(rc);
    }
}
