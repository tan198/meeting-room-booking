package com.tanle.meeting_room_booking.team.repository;

import com.tanle.meeting_room_booking.team.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    Optional<Team> findByCodeIgnoreCase(String code);

    long countByIdIn(Collection<UUID> ids);
}
