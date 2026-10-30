package com.tanle.meeting_room_booking.issue.repository;

import com.tanle.meeting_room_booking.issue.entity.RoomIssueHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoomIssueHistoryRepository extends JpaRepository<RoomIssueHistory, UUID> {
    List<RoomIssueHistory> findByIssueIdOrderByCreatedAtDesc(UUID issueId);
}

