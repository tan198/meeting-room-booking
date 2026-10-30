package com.tanle.meeting_room_booking.group.repository;

import com.tanle.meeting_room_booking.group.entity.WorkGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface WorkGroupRepository extends JpaRepository<WorkGroup, UUID> {

    boolean existsByCodeIgnoreCase(String code);

    List<WorkGroup> findByTypeIgnoreCaseOrderByCodeAsc(String type);

    long countByIdIn(Collection<UUID> ids);
}
