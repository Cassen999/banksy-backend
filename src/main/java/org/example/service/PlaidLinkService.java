package org.example.service;

import com.plaid.client.model.*;
import com.plaid.client.request.PlaidApi;
import org.example.entity.PlaidAccount;
import org.example.entity.PlaidItem;
import org.example.entity.User;
import org.example.plaid.PlaidClientFactory;
import org.example.repository.PlaidAccountRepository;
import org.example.repository.PlaidItemRepository;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import retrofit2.Response;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PlaidLinkService {

    private final PlaidApi plaidClient;
    private final EncryptionService encryptionService;
    private final PlaidItemRepository plaidItemRepository;
    private final PlaidAccountRepository plaidAccountRepository;
    private final UserRepository userRepository;

    public PlaidLinkService(PlaidApi plaidClient,
                            EncryptionService encryptionService,
                            PlaidItemRepository plaidItemRepository,
                            PlaidAccountRepository plaidAccountRepository,
                            UserRepository userRepository) {
        this.plaidClient = plaidClient;
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

        Response<LinkTokenCreateResponse> response = plaidClient.linkTokenCreate(request).execute();

        if (!response.isSuccessful() || response.body() == null) {
            throw new RuntimeException("Failed to create Plaid link token: " + PlaidClientFactory.extractErrorDetail(response));
        }

        return response.body().getLinkToken();
    }

    @Transactional
    public PlaidItem exchangeAndStore(String publicToken,
                                      String institutionId,
                                      String institutionName,
                                      UUID userId) throws IOException {
        User user = userRepository.findByIdWithPlaidItems(userId).orElseThrow();

        Response<ItemPublicTokenExchangeResponse> exchangeResponse = plaidClient
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
            if (!user.getPlaidItems().contains(existingItem)) {
                user.getPlaidItems().add(existingItem);
            }
            return existingItem;
        }

        PlaidItem item = new PlaidItem();
        item.setAccessTokenEnc(encryptionService.encrypt(accessToken));
        item.setItemId(itemId);
        item.setInstitutionId(institutionId);
        item.setInstitutionName(institutionName);
        plaidItemRepository.save(item);

        Response<AccountsGetResponse> accountsResponse = plaidClient
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
        return item;
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
