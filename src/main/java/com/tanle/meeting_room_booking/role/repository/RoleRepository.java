package com.tanle.meeting_room_booking.role.repository;

import com.tanle.meeting_room_booking.role.entity.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByName(String name);

    @EntityGraph(attributePaths = {"permissions"})
    Optional<Role> findWithPermissionsByName(String name);

    @EntityGraph(attributePaths = {"permissions"})
    Optional<Role> findWithPermissionsById(UUID id);

    @Override
    @EntityGraph(attributePaths = {"permissions"})
    List<Role> findAll();

    boolean existsByName(String name);
}
