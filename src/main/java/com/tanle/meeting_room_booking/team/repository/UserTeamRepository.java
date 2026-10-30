package com.tanle.meeting_room_booking.team.repository;

import com.tanle.meeting_room_booking.team.entity.UserTeam;
import com.tanle.meeting_room_booking.team.entity.UserTeamId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface UserTeamRepository extends JpaRepository<UserTeam, UserTeamId> {

    @EntityGraph(attributePaths = {"user", "team"})
    List<UserTeam> findById_TeamId(UUID teamId);

    @EntityGraph(attributePaths = {"user", "team"})
    List<UserTeam> findById_TeamIdIn(Collection<UUID> teamIds);

    @EntityGraph(attributePaths = {"team"})
    List<UserTeam> findById_UserId(UUID userId);

    @EntityGraph(attributePaths = {"team"})
    List<UserTeam> findById_UserIdIn(Collection<UUID> userIds);

    void deleteById_UserId(UUID userId);

    @Query("""
            SELECT DISTINCT ut.user.id FROM UserTeam ut
            JOIN ut.user u
            WHERE ut.team.id IN :teamIds
              AND u.isActive = true
            """)
    List<UUID> findActiveUserIdsByTeamIds(@Param("teamIds") Collection<UUID> teamIds);
}
