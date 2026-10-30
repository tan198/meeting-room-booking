package com.tanle.meeting_room_booking.issue.repository;

import com.tanle.meeting_room_booking.issue.entity.IssueStatus;
import com.tanle.meeting_room_booking.issue.entity.RoomIssue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RoomIssueRepository extends JpaRepository<RoomIssue, UUID> {

    @EntityGraph(attributePaths = {"room", "reporter"})
    Optional<RoomIssue> findWithDetailsById(UUID id);

    @Query(value = """
            SELECT ri FROM RoomIssue ri
            JOIN FETCH ri.room r
            JOIN FETCH ri.reporter rep
            WHERE (:reporterId IS NULL OR rep.id = :reporterId)
              AND (:roomId IS NULL OR r.id = :roomId)
              AND (:status IS NULL OR ri.status = :status)
            """,
            countQuery = """
            SELECT COUNT(ri) FROM RoomIssue ri
            WHERE (:reporterId IS NULL OR ri.reporter.id = :reporterId)
              AND (:roomId IS NULL OR ri.room.id = :roomId)
              AND (:status IS NULL OR ri.status = :status)
            """)
    Page<RoomIssue> searchIssues(
            @Param("reporterId") UUID reporterId,
            @Param("roomId") UUID roomId,
            @Param("status") IssueStatus status,
            Pageable pageable
    );
}

