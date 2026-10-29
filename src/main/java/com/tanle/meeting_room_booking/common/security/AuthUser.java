package com.tanle.meeting_room_booking.common.security;

import java.util.Set;
import java.util.UUID;

public record AuthUser(UUID id, String email, String role, Set<String> permissions) {

    public boolean hasAuthority(String authority) {
        return permissions != null && permissions.contains(authority);
    }
}
