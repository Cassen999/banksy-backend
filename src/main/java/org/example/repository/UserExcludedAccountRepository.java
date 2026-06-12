package org.example.repository;

import org.example.entity.UserExcludedAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;
import java.util.UUID;

public interface UserExcludedAccountRepository extends JpaRepository<UserExcludedAccount, UUID> {

    @Query("SELECT e.plaidAccountId FROM UserExcludedAccount e WHERE e.user.id = :userId")
    Set<String> findPlaidAccountIdsByUserId(@Param("userId") UUID userId);
}
