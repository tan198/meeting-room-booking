package com.tanle.meeting_room_booking.permission.repository;

import com.tanle.meeting_room_booking.permission.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    Optional<Permission> findByCode(String code);

    boolean existsByCode(String code);
}
