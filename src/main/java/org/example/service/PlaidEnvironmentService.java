package org.example.service;

import com.plaid.client.request.PlaidApi;
import jakarta.annotation.PostConstruct;
import org.example.entity.PlaidEnvironmentConfig;
import org.example.plaid.PlaidClientFactory;
import org.example.repository.PlaidEnvironmentConfigRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class PlaidEnvironmentService {

    private static final int CONFIG_ROW_ID = 1;

    private final PlaidEnvironmentConfigRepository repo;
    private volatile PlaidApi currentClient;

    public PlaidEnvironmentService(PlaidEnvironmentConfigRepository repo) {
        this.repo = repo;
    }

    @PostConstruct
    public void init() {
        String env = repo.findById(CONFIG_ROW_ID)
                .map(PlaidEnvironmentConfig::getEnv)
                .orElse("sandbox");
        currentClient = PlaidClientFactory.create(env);
    }

    public PlaidApi getClient() {
        return currentClient;
    }

    public String getCurrentEnvironment() {
        return repo.findById(CONFIG_ROW_ID)
                .map(PlaidEnvironmentConfig::getEnv)
                .orElse("sandbox");
    }

    public synchronized String toggle() {
        PlaidEnvironmentConfig config = repo.findById(CONFIG_ROW_ID).orElseThrow();
        String next = "sandbox".equals(config.getEnv()) ? "production" : "sandbox";

        config.setEnv(next);
        config.setUpdatedAt(OffsetDateTime.now());
        repo.save(config);

        currentClient = PlaidClientFactory.create(next);
        return next;
    }
}
