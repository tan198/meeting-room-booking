package com.tanle.meeting_room_booking.common.security;

import java.util.UUID;

public record AuthUser(UUID id, String email, String role) {
}
