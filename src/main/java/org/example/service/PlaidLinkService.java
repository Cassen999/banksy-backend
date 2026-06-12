package org.example.service;

import com.plaid.client.model.*;
import org.example.entity.PlaidAccount;
import org.example.entity.PlaidItem;
import org.example.entity.PlaidItemStatus;
import org.example.entity.User;
import org.example.model.RelinkSignal;
import org.example.plaid.PlaidClientFactory;
import org.example.plaid.PlaidTokenError;
import org.example.repository.PlaidAccountRepository;
import org.example.repository.PlaidItemRepository;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import retrofit2.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PlaidLinkService {

    private final PlaidEnvironmentService plaidEnvService;
    private final EncryptionService encryptionService;
    private final PlaidItemRepository plaidItemRepository;
    private final PlaidAccountRepository plaidAccountRepository;
    private final UserRepository userRepository;

    public PlaidLinkService(PlaidEnvironmentService plaidEnvService,
                            EncryptionService encryptionService,
                            PlaidItemRepository plaidItemRepository,
                            PlaidAccountRepository plaidAccountRepository,
                            UserRepository userRepository) {
        this.plaidEnvService = plaidEnvService;
        this.encryptionService = encryptionService;
        this.plaidItemRepository = plaidItemRepository;
        this.plaidAccountRepository = plaidAccountRepository;
        this.userRepository = userRepository;
    }

    public String createLinkToken(UUID userId) throws IOException {
        LinkTokenCreateRequest request = new LinkTokenCreateRequest()
                .user(new LinkTokenCreateRequestUser().clientUserId(userId.toString()))
                .clientName("Banksy")
                .products(List.of(Products.TRANSACTIONS))
                .countryCodes(List.of(CountryCode.US))
                .language("en");

        Response<LinkTokenCreateResponse> response = plaidEnvService.getClient().linkTokenCreate(request).execute();

        if (!response.isSuccessful() || response.body() == null) {
            throw new RuntimeException("Failed to create Plaid link token: " + PlaidClientFactory.extractErrorDetail(response));
        }

        return response.body().getLinkToken();
    }

    @Transactional(readOnly = true)
    public String linkTokenRefresh(UUID userId, UUID plaidItemId) throws IOException {
        PlaidItem item = loadAndVerifyOwner(plaidItemId, userId);
        String accessToken = encryptionService.decrypt(item.getAccessTokenEnc());

        LinkTokenCreateRequest request = new LinkTokenCreateRequest()
                .user(new LinkTokenCreateRequestUser().clientUserId(userId.toString()))
                .clientName("Banksy")
                .accessToken(accessToken)
                .countryCodes(List.of(CountryCode.US))
                .language("en");

        Response<LinkTokenCreateResponse> response = plaidEnvService.getClient().linkTokenCreate(request).execute();

        if (!response.isSuccessful() || response.body() == null) {
            throw new RuntimeException("Failed to create Plaid refresh link token: " + PlaidClientFactory.extractErrorDetail(response));
        }

        return response.body().getLinkToken();
    }

    @Transactional(readOnly = true)
    public String fullRelinkToken(UUID userId, UUID plaidItemId) throws IOException {
        loadAndVerifyOwner(plaidItemId, userId);

        LinkTokenCreateRequest request = new LinkTokenCreateRequest()
                .user(new LinkTokenCreateRequestUser().clientUserId(userId.toString()))
                .clientName("Banksy")
                .products(List.of(Products.TRANSACTIONS))
                .countryCodes(List.of(CountryCode.US))
                .language("en");

        Response<LinkTokenCreateResponse> response = plaidEnvService.getClient().linkTokenCreate(request).execute();

        if (!response.isSuccessful() || response.body() == null) {
            throw new RuntimeException("Failed to create Plaid full relink token: " + PlaidClientFactory.extractErrorDetail(response));
        }

        return response.body().getLinkToken();
    }

    private PlaidItem loadAndVerifyOwner(UUID plaidItemId, UUID userId) {
        PlaidItem item = plaidItemRepository.findById(plaidItemId)
                .orElseThrow(() -> new IllegalArgumentException("Bank connection not found"));
        if (!item.getOwner().getId().equals(userId)) {
            throw new SecurityException("You do not have access to this bank connection");
        }
        return item;
    }

    @Transactional
    public void replaceExpiredItem(UUID oldItemId, UUID newItemId) {
        PlaidItem oldItem = plaidItemRepository.findById(oldItemId)
                .orElseThrow(() -> new IllegalArgumentException("Old bank connection not found"));
        PlaidItem newItem = plaidItemRepository.findById(newItemId)
                .orElseThrow(() -> new IllegalArgumentException("New bank connection not found"));

        List<User> usersWithOldItem = userRepository.findAllWithPlaidItem(oldItemId);
        for (User u : usersWithOldItem) {
            User loaded = userRepository.findByIdWithPlaidItems(u.getId()).orElseThrow();
            loaded.getPlaidItems().removeIf(pi -> pi.getId().equals(oldItemId));
            if (!loaded.getPlaidItems().contains(newItem)) {
                loaded.getPlaidItems().add(newItem);
            }
        }

        plaidItemRepository.delete(oldItem);
    }

    @Transactional(readOnly = true)
    public List<RelinkSignal> getRelinkStatus(UUID userId) {
        User user = userRepository.findByIdWithPlaidItems(userId).orElseThrow();
        List<RelinkSignal> signals = new ArrayList<>();

        for (PlaidItem item : user.getPlaidItems()) {
            if (item.getStatus() != PlaidItemStatus.HEALTHY) {
                signals.add(RelinkSignal.from(item, userId,
                        RelinkSignal.errorTypeFromStatus(item.getStatus())));
            }
        }

        return signals;
    }

    @Transactional
    public UUID exchangeAndStore(String publicToken,
                                      String institutionId,
                                      String institutionName,
                                      UUID userId) throws IOException {
        User user = userRepository.findByIdWithPlaidItems(userId).orElseThrow();

        Response<ItemPublicTokenExchangeResponse> exchangeResponse = plaidEnvService.getClient()
                .itemPublicTokenExchange(new ItemPublicTokenExchangeRequest().publicToken(publicToken))
                .execute();

        if (!exchangeResponse.isSuccessful() || exchangeResponse.body() == null) {
            throw new RuntimeException("Failed to exchange public token: " + PlaidClientFactory.extractErrorDetail(exchangeResponse));
        }

        String accessToken = exchangeResponse.body().getAccessToken();
        String itemId = exchangeResponse.body().getItemId();

        Optional<PlaidItem> existing = plaidItemRepository.findByItemId(itemId);
        if (existing.isPresent()) {
            PlaidItem existingItem = existing.get();
            String storedToken = encryptionService.decrypt(existingItem.getAccessTokenEnc());
            if (!storedToken.equals(accessToken)) {
                existingItem.setAccessTokenEnc(encryptionService.encrypt(accessToken));
            }
            existingItem.setStatus(PlaidItemStatus.HEALTHY);
            plaidItemRepository.save(existingItem);
            if (!user.getPlaidItems().contains(existingItem)) {
                user.getPlaidItems().add(existingItem);
            }
            return existingItem.getId();
        }

        PlaidItem item = new PlaidItem();
        item.setAccessTokenEnc(encryptionService.encrypt(accessToken));
        item.setItemId(itemId);
        item.setInstitutionId(institutionId);
        item.setInstitutionName(institutionName);
        item.setOwner(user);
        item.setStatus(PlaidItemStatus.HEALTHY);
        plaidItemRepository.save(item);

        Response<AccountsGetResponse> accountsResponse = plaidEnvService.getClient()
                .accountsGet(new AccountsGetRequest().accessToken(accessToken))
                .execute();

        if (accountsResponse.isSuccessful() && accountsResponse.body() != null) {
            for (AccountBase ab : accountsResponse.body().getAccounts()) {
                if (!plaidAccountRepository.existsByPlaidAccountId(ab.getAccountId())) {
                    PlaidAccount account = new PlaidAccount();
                    account.setPlaidItem(item);
                    account.setPlaidAccountId(ab.getAccountId());
                    account.setName(ab.getName());
                    account.setOfficialName(ab.getOfficialName());
                    account.setType(ab.getType() != null ? ab.getType().getValue() : null);
                    account.setSubtype(ab.getSubtype() != null ? ab.getSubtype().getValue() : null);
                    account.setMask(ab.getMask());
                    plaidAccountRepository.save(account);
                }
            }
        }

        user.getPlaidItems().add(item);
        return item.getId();
    }

    @Transactional
    public void shareItem(UUID plaidItemId, UUID requestingUserId, String targetEmail) {
        User requestingUser = userRepository.findByIdWithPlaidItems(requestingUserId).orElseThrow();

        PlaidItem item = plaidItemRepository.findById(plaidItemId)
                .orElseThrow(() -> new IllegalArgumentException("Bank connection not found"));

        boolean owns = requestingUser.getPlaidItems().stream()
                .anyMatch(i -> i.getId().equals(plaidItemId));
        if (!owns) {
            throw new SecurityException("You do not have access to this bank connection");
        }

        UUID targetUserId = userRepository.findByEmail(targetEmail)
                .orElseThrow(() -> new IllegalArgumentException("No user found with that email"))
                .getId();
        User targetUser = userRepository.findByIdWithPlaidItems(targetUserId).orElseThrow();

        boolean alreadyShared = targetUser.getPlaidItems().stream()
                .anyMatch(i -> i.getId().equals(plaidItemId));
        if (!alreadyShared) {
            targetUser.getPlaidItems().add(item);
        }
    }
}
