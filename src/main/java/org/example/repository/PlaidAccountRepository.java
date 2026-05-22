package org.example.repository;

import org.example.entity.PlaidAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface PlaidAccountRepository extends JpaRepository<PlaidAccount, UUID> {
    boolean existsByPlaidAccountId(String plaidAccountId);

    @Query("SELECT a FROM PlaidAccount a JOIN FETCH a.plaidItem WHERE a.id = :id")
    Optional<PlaidAccount> findByIdWithItem(@Param("id") UUID id);

    @Query("SELECT a.plaidAccountId FROM PlaidAccount a WHERE a.plaidItem.id = :itemId AND a.hidden = true")
    Set<String> findHiddenAccountIdsByItemId(@Param("itemId") UUID itemId);
}
