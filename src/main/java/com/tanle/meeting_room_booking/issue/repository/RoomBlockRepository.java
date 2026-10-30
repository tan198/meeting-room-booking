package com.tanle.meeting_room_booking.issue.repository;

import com.tanle.meeting_room_booking.issue.entity.RoomBlock;
import com.tanle.meeting_room_booking.issue.entity.RoomBlockStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomBlockRepository extends JpaRepository<RoomBlock, UUID> {

    @EntityGraph(attributePaths = {"room", "issue"})
    Optional<RoomBlock> findWithDetailsById(UUID id);

    /**
     * Kiểm tra phòng có khoảng chặn (RoomBlock) đang ACTIVE và còn hiệu lực giao với [startTime, endTime) hay không (Spec 7.1 & 11.1).
     */
    @Query("""
            SELECT COUNT(rb) > 0 FROM RoomBlock rb
            WHERE rb.room.id = :roomId
              AND rb.status = com.tanle.meeting_room_booking.issue.entity.RoomBlockStatus.ACTIVE
              AND rb.startTime < :endTime
              AND rb.endTime > :startTime
              AND (:excludeBlockId IS NULL OR rb.id <> :excludeBlockId)
            """)
    boolean existsOverlappingActiveBlock(
            @Param("roomId") UUID roomId,
            @Param("startTime") OffsetDateTime startTime,
            @Param("endTime") OffsetDateTime endTime,
            @Param("excludeBlockId") UUID excludeBlockId
    );

    @EntityGraph(attributePaths = {"room", "issue"})
    @Query("""
            SELECT rb FROM RoomBlock rb
            WHERE rb.room.id = :roomId
              AND rb.status = com.tanle.meeting_room_booking.issue.entity.RoomBlockStatus.ACTIVE
              AND rb.startTime < :toTime
              AND rb.endTime > :fromTime
            ORDER BY rb.startTime ASC
            """)
    List<RoomBlock> findActiveBlocksForRoomInRange(
            @Param("roomId") UUID roomId,
            @Param("fromTime") OffsetDateTime fromTime,
            @Param("toTime") OffsetDateTime toTime
    );

    @Query(value = """
            SELECT rb FROM RoomBlock rb
            JOIN FETCH rb.room r
            LEFT JOIN FETCH rb.issue i
            WHERE (:roomId IS NULL OR r.id = :roomId)
              AND (:status IS NULL OR rb.status = :status)
            """,
            countQuery = """
            SELECT COUNT(rb) FROM RoomBlock rb
            WHERE (:roomId IS NULL OR rb.room.id = :roomId)
              AND (:status IS NULL OR rb.status = :status)
            """)
    Page<RoomBlock> searchBlocks(
            @Param("roomId") UUID roomId,
            @Param("status") RoomBlockStatus status,
            Pageable pageable
    );
}

