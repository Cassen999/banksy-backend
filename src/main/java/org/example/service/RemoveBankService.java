package org.example.service;

import com.plaid.client.model.ItemRemoveRequest;
import com.plaid.client.model.ItemRemoveResponse;
import com.plaid.client.request.PlaidApi;
import org.example.entity.PlaidAccount;
import org.example.entity.PlaidItem;
import org.example.entity.PlaidItemStatus;
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
import java.util.UUID;

@Service
public class RemoveBankService {

    static final String ERROR_MESSAGE =
            "There was an error removing your account, please try again in a few minutes or contact Cassen";

    private final PlaidApi plaidClient;
    private final EncryptionService encryptionService;
    private final PlaidItemRepository plaidItemRepository;
    private final PlaidAccountRepository plaidAccountRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public RemoveBankService(PlaidApi plaidClient,
                             EncryptionService encryptionService,
                             PlaidItemRepository plaidItemRepository,
                             PlaidAccountRepository plaidAccountRepository,
                             UserRepository userRepository,
                             NotificationService notificationService) {
        this.plaidClient = plaidClient;
        this.encryptionService = encryptionService;
        this.plaidItemRepository = plaidItemRepository;
        this.plaidAccountRepository = plaidAccountRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public void hideAccount(UUID plaidAccountId, UUID requestingUserId) {
        PlaidAccount account = plaidAccountRepository.findByIdWithItem(plaidAccountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        List<User> linkedUsers = userRepository.findAllWithPlaidItem(account.getPlaidItem().getId());
        List<UUID> linkedUserIds = linkedUsers.stream().map(User::getId).toList();

        if (linkedUsers.stream().noneMatch(u -> u.getId().equals(requestingUserId))) {
            throw new SecurityException("You do not have access to this account");
        }

        String mask = account.getMask() != null ? account.getMask() : "????";

        try {
            account.setHidden(true);
            plaidAccountRepository.save(account);
            notificationService.notifyAll(linkedUserIds, "Removed bank account ending in " + mask);
        } catch (Exception e) {
            notificationService.notifyAll(linkedUserIds, ERROR_MESSAGE);
            throw e;
        }
    }

    @Transactional
    public void removeItem(UUID plaidItemId, UUID requestingUserId) throws IOException {
        PlaidItem item = plaidItemRepository.findById(plaidItemId)
                .orElseThrow(() -> new IllegalArgumentException("Bank connection not found"));

        List<User> linkedUsers = userRepository.findAllWithPlaidItem(plaidItemId);
        List<UUID> linkedUserIds = linkedUsers.stream().map(User::getId).toList();

        if (linkedUsers.stream().noneMatch(u -> u.getId().equals(requestingUserId))) {
            throw new SecurityException("You do not have access to this bank connection");
        }

        String institutionName = item.getInstitutionName();

        try {
            if (item.getStatus() != PlaidItemStatus.INVALID_TOKEN) {
                String accessToken = encryptionService.decrypt(item.getAccessTokenEnc());
                Response<ItemRemoveResponse> response = plaidClient
                        .itemRemove(new ItemRemoveRequest().accessToken(accessToken))
                        .execute();
                if (!response.isSuccessful()) {
                    throw new RuntimeException("Plaid item removal failed: "
                            + PlaidClientFactory.extractErrorDetail(response));
                }
            }

            plaidItemRepository.delete(item);
            notificationService.notifyAll(linkedUserIds,
                    "Removed " + institutionName + " from Banksy, in order to see these accounts again you must re-link bank.");
        } catch (IOException | RuntimeException e) {
            notificationService.notifyAll(linkedUserIds, ERROR_MESSAGE);
            throw e;
        }
    }
}
