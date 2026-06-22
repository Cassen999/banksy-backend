package org.example.service;

import org.example.entity.PlaidAccount;
import org.example.entity.PlaidItem;
import org.example.entity.User;
import org.example.entity.UserAccountName;
import org.example.repository.PlaidAccountRepository;
import org.example.repository.UserAccountNameRepository;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class AccountCustomizationService {

    private final UserAccountNameRepository accountNameRepository;
    private final PlaidAccountRepository plaidAccountRepository;
    private final UserRepository userRepository;

    public AccountCustomizationService(UserAccountNameRepository accountNameRepository,
                                       PlaidAccountRepository plaidAccountRepository,
                                       UserRepository userRepository) {
        this.accountNameRepository = accountNameRepository;
        this.plaidAccountRepository = plaidAccountRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void setCustomName(UUID userId, String plaidAccountId, String customName) {
        User user = verifyAccess(userId, plaidAccountId);

        Optional<UserAccountName> existing =
                accountNameRepository.findByUserIdAndPlaidAccountId(userId, plaidAccountId);

        if (existing.isPresent()) {
            existing.get().setCustomName(customName);
            accountNameRepository.save(existing.get());
        } else {
            UserAccountName entry = new UserAccountName();
            entry.setUser(user);
            entry.setPlaidAccountId(plaidAccountId);
            entry.setCustomName(customName);
            accountNameRepository.save(entry);
        }
    }

    @Transactional
    public void deleteCustomName(UUID userId, String plaidAccountId) {
        verifyAccess(userId, plaidAccountId);

        accountNameRepository.findByUserIdAndPlaidAccountId(userId, plaidAccountId)
                .ifPresent(accountNameRepository::delete);
    }

    private User verifyAccess(UUID userId, String plaidAccountId) {
        PlaidAccount account = plaidAccountRepository.findByPlaidAccountIdWithItem(plaidAccountId)
                .orElseThrow(() -> new NoSuchElementException("Account not found: " + plaidAccountId));

        PlaidItem item = account.getPlaidItem();
        User user = userRepository.findByIdWithPlaidItems(userId).orElseThrow();
        boolean linked = user.getPlaidItems().stream()
                .anyMatch(pi -> pi.getId().equals(item.getId()));
        if (!linked) {
            throw new SecurityException("Not authorized to access this account");
        }
        return user;
    }
}
