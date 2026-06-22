package org.example.repository;

import org.example.entity.UserAccountName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountNameRepository extends JpaRepository<UserAccountName, UUID> {

    @Query("SELECT u FROM UserAccountName u WHERE u.user.id = :userId AND u.plaidAccountId = :plaidAccountId")
    Optional<UserAccountName> findByUserIdAndPlaidAccountId(@Param("userId") UUID userId,
                                                             @Param("plaidAccountId") String plaidAccountId);

    @Query("SELECT u FROM UserAccountName u WHERE u.user.id = :userId")
    List<UserAccountName> findAllByUserId(@Param("userId") UUID userId);
}
