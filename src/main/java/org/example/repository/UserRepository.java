package org.example.repository;

import org.example.entity.User;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.plaidItems WHERE u.id = :id")
    Optional<User> findByIdWithPlaidItems(@Param("id") UUID id);

    @Query("SELECT u FROM User u JOIN u.plaidItems pi WHERE pi.id = :plaidItemId")
    List<User> findAllWithPlaidItem(@Param("plaidItemId") UUID plaidItemId);
}
