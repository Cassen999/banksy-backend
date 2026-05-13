package org.example.repository;

import org.example.entity.PlaidAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PlaidAccountRepository extends JpaRepository<PlaidAccount, UUID> {
    boolean existsByPlaidAccountId(String plaidAccountId);
}
