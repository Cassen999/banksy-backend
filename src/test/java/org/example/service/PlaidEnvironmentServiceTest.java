package org.example.service;

import com.plaid.client.request.PlaidApi;
import org.example.entity.PlaidEnvironmentConfig;
import org.example.repository.PlaidEnvironmentConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaidEnvironmentServiceTest {

    @Mock private PlaidEnvironmentConfigRepository repo;

    private PlaidEnvironmentService service;

    private PlaidEnvironmentConfig sandboxConfig() {
        PlaidEnvironmentConfig c = new PlaidEnvironmentConfig();
        c.setEnv("sandbox");
        return c;
    }

    private PlaidEnvironmentConfig productionConfig() {
        PlaidEnvironmentConfig c = new PlaidEnvironmentConfig();
        c.setEnv("production");
        return c;
    }

    @BeforeEach
    void setUp() {
        when(repo.findById(1)).thenReturn(Optional.of(sandboxConfig()));
        service = new PlaidEnvironmentService(repo);
        service.init();
    }

    @Test
    void shouldReturnCurrentEnvironment() {
        assertThat(service.getCurrentEnvironment()).isEqualTo("sandbox");
    }

    @Test
    void shouldReturnNonNullClient() {
        assertThat(service.getClient()).isNotNull();
    }

    @Test
    void shouldToggleFromSandboxToProduction() {
        // setUp initialized to "sandbox" — toggle should flip to "production"
        String result = service.toggle();

        assertThat(result).isEqualTo("production");
        ArgumentCaptor<PlaidEnvironmentConfig> captor = ArgumentCaptor.forClass(PlaidEnvironmentConfig.class);
        verify(repo, atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues()).anyMatch(c -> "production".equals(c.getEnv()));
    }

    @Test
    void shouldToggleFromProductionBackToSandbox() {
        // Re-initialize service with production state
        when(repo.findById(1)).thenReturn(Optional.of(productionConfig()));
        service.init();

        String result = service.toggle();

        assertThat(result).isEqualTo("sandbox");
    }

    @Test
    void shouldReturnDifferentClientInstance_afterToggle() {
        PlaidApi clientBefore = service.getClient();

        service.toggle();
        PlaidApi clientAfter = service.getClient();

        assertThat(clientAfter).isNotSameAs(clientBefore);
    }
}
