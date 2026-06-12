package org.example.repository;

import org.example.entity.PlaidCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PlaidCategoryRepositoryTest extends AbstractRepositoryTest {

    @Autowired private PlaidCategoryRepository categoryRepository;

    @Test
    void shouldContainSeededRows() {
        assertThat(categoryRepository.count()).isGreaterThan(0);
    }

    @Test
    void shouldContainBothPrimaryAndDetailedTypes() {
        List<PlaidCategory> all = categoryRepository.findAll();
        assertThat(all).anyMatch(c -> "PRIMARY".equals(c.getCategoryType()));
        assertThat(all).anyMatch(c -> "DETAILED".equals(c.getCategoryType()));
    }

    @Test
    void shouldHaveNullPrimaryCategory_forPrimaryTypeRow() {
        Optional<PlaidCategory> result = categoryRepository.findById("FOOD_AND_DRINK");

        assertThat(result).isPresent();
        assertThat(result.get().getCategoryType()).isEqualTo("PRIMARY");
        assertThat(result.get().getPrimaryCategory()).isNull();
    }

    @Test
    void shouldHaveParentPrimaryCategory_forDetailedTypeRow() {
        Optional<PlaidCategory> result = categoryRepository.findById("RENT_AND_UTILITIES_RENT");

        assertThat(result).isPresent();
        assertThat(result.get().getCategoryType()).isEqualTo("DETAILED");
        assertThat(result.get().getPrimaryCategory()).isEqualTo("RENT_AND_UTILITIES");
    }

    @Test
    void shouldContainGymDetailedCategory() {
        Optional<PlaidCategory> result = categoryRepository.findById("PERSONAL_CARE_GYMS_AND_FITNESS_CENTERS");

        assertThat(result).isPresent();
        assertThat(result.get().getPrimaryCategory()).isEqualTo("PERSONAL_CARE");
    }
}
