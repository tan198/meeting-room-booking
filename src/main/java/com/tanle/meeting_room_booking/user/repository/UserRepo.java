package com.tanle.meeting_room_booking.user.repository;

import com.tanle.meeting_room_booking.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepo extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Loads role + permissions in one query (used by login and per-request authentication). */
    @EntityGraph(attributePaths = {"role", "role.permissions"})
    Optional<User> findWithRoleByEmail(String email);

    @EntityGraph(attributePaths = {"role", "role.permissions"})
    Optional<User> findWithRoleById(UUID id);

    /**
     * {@code keyword} must be non-null and have SQL wildcard characters ({@code \}, {@code %}, {@code _})
     * escaped with {@code \}; pass an empty string for "no keyword filter"
     * (a null String parameter inside LOWER/CONCAT is typed as bytea by PostgreSQL and fails).
     */
    @Query(value = """
          SELECT u FROM User u LEFT JOIN FETCH u.role r
          WHERE (:keyword = '' OR LOWER(u.fullName) LIKE CONCAT('%', :keyword, '%') ESCAPE '\\'
                 OR LOWER(u.email) LIKE CONCAT('%', :keyword, '%') ESCAPE '\\')
            AND (:roleName IS NULL OR r.name = :roleName)
            AND (:isActive IS NULL OR u.isActive = :isActive)
          """,
            countQuery = """
          SELECT COUNT(u) FROM User u LEFT JOIN u.role r
          WHERE (:keyword = '' OR LOWER(u.fullName) LIKE CONCAT('%', :keyword, '%') ESCAPE '\\'
                 OR LOWER(u.email) LIKE CONCAT('%', :keyword, '%') ESCAPE '\\')
            AND (:roleName IS NULL OR r.name = :roleName)
            AND (:isActive IS NULL OR u.isActive = :isActive)
          """)
    Page<User> searchUsers(
            @Param("keyword") String keyword,
            @Param("roleName") String roleName,
            @Param("isActive") Boolean isActive,
            Pageable pageable
    );
}
