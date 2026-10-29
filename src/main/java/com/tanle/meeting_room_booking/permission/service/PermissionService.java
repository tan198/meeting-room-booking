package com.tanle.meeting_room_booking.permission.service;

import com.tanle.meeting_room_booking.permission.dto.PermissionResponse;
import com.tanle.meeting_room_booking.permission.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionService {

    private final PermissionRepository permissionRepository;

    public List<PermissionResponse> findAll() {
        return permissionRepository.findAll(Sort.by(Sort.Order.asc("code")))
                .stream()
                .map(PermissionResponse::from)
                .toList();
    }
}
