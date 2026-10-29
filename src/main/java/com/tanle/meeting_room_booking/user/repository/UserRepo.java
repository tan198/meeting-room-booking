package com.tanle.meeting_room_booking.user.repository;

import com.tanle.meeting_room_booking.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepo extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole_Id(UUID roleId);

    @Query("""
            SELECT u.id FROM User u
            JOIN u.role r
            WHERE u.isActive = true
              AND UPPER(r.name) = 'ADMIN'
            """)
    List<UUID> findActiveAdminIds();

    /** Loads role + permissions in one query (used by login and per-request authentication). */
    @EntityGraph(attributePaths = {"role", "role.permissions"})
    Optional<User> findWithRoleByEmail(String email);

    @EntityGraph(attributePaths = {"role", "role.permissions"})
    Optional<User> findWithRoleById(UUID id);

    /**
     * Khóa bi quan (SELECT ... FOR UPDATE) một User để đồng bộ hóa việc xoay vòng / thu hồi toàn bộ Refresh Token.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"role", "role.permissions"})
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Khóa bi quan danh sách User theo thứ tự UUID tăng dần (chống Deadlock) để kiểm tra và ghi lịch họp cá nhân nguyên tử.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id IN :ids ORDER BY u.id ASC")
    List<User> findAllByIdInOrderByIdForUpdate(@Param("ids") Collection<UUID> ids);

    /**
     * Chỉ JOIN FETCH u.role (quan hệ ManyToOne 1-1), KHÔNG JOIN FETCH r.permissions (collection 1-N)
     * để PostgreSQL thực hiện LIMIT/OFFSET trực tiếp dưới DB thay vì phân trang trong bộ nhớ (HHH90003004).
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
