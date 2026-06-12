package org.example.repository;

import org.example.entity.UserRejectedCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;
import java.util.UUID;

public interface UserRejectedCategoryRepository extends JpaRepository<UserRejectedCategory, UUID> {

    @Query("SELECT r.category FROM UserRejectedCategory r WHERE r.user.id = :userId")
    Set<String> findCategoriesByUserId(@Param("userId") UUID userId);
}
