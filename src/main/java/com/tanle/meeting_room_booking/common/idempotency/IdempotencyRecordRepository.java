package com.tanle.meeting_room_booking.common.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    @Query("""
            SELECT ir FROM IdempotencyRecord ir
            WHERE ir.userId = :userId
              AND ir.operationKey = :operationKey
              AND ir.idempotencyKey = :idempotencyKey
              AND ir.expiresAt > :now
            """)
    Optional<IdempotencyRecord> findValidRecord(
            @Param("userId") UUID userId,
            @Param("operationKey") String operationKey,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("now") OffsetDateTime now
    );

    @Modifying
    @Query("DELETE FROM IdempotencyRecord ir WHERE ir.expiresAt <= :now")
    int deleteExpiredRecords(@Param("now") OffsetDateTime now);
}

