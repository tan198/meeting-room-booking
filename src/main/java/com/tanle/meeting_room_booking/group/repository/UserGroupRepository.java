package com.tanle.meeting_room_booking.group.repository;

import com.tanle.meeting_room_booking.group.entity.UserGroup;
import com.tanle.meeting_room_booking.group.entity.UserGroupId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface UserGroupRepository extends JpaRepository<UserGroup, UserGroupId> {

    @EntityGraph(attributePaths = {"user", "group"})
    List<UserGroup> findById_GroupId(UUID groupId);

    @EntityGraph(attributePaths = {"user", "group"})
    List<UserGroup> findById_GroupIdIn(Collection<UUID> groupIds);

    @Query("""
            SELECT DISTINCT ug.user.id FROM UserGroup ug
            JOIN ug.user u
            WHERE ug.group.id IN :groupIds
              AND u.isActive = true
            """)
    List<UUID> findActiveUserIdsByGroupIds(@Param("groupIds") Collection<UUID> groupIds);
}

