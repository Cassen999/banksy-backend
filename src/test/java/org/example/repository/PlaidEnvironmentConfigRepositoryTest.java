package org.example.repository;

import org.example.entity.PlaidEnvironmentConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PlaidEnvironmentConfigRepositoryTest extends AbstractRepositoryTest {

    @Autowired private PlaidEnvironmentConfigRepository repo;

    @Test
    void shouldLoadSeedRow() {
        Optional<PlaidEnvironmentConfig> config = repo.findById(1);
        assertThat(config).isPresent();
        assertThat(config.get().getEnv()).isEqualTo("sandbox");
    }

    @Test
    void shouldPersistEnvUpdate() {
        PlaidEnvironmentConfig config = repo.findById(1).orElseThrow();
        config.setEnv("production");
        repo.save(config);

        assertThat(repo.findById(1).orElseThrow().getEnv()).isEqualTo("production");
    }
}
