package com.tanle.meeting_room_booking.announcement.repository;

import com.tanle.meeting_room_booking.announcement.entity.Announcement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {
    Page<Announcement> findAllByOrderByPublishedAtDesc(Pageable pageable);
}

