package com.tanle.meeting_room_booking.room.controller;

import com.tanle.meeting_room_booking.common.response.ApiResponse;
import com.tanle.meeting_room_booking.common.response.PageResponse;
import com.tanle.meeting_room_booking.room.dto.CreateRoomRequest;
import com.tanle.meeting_room_booking.room.dto.RoomResponse;
import com.tanle.meeting_room_booking.room.dto.UpdateRoomRequest;
import com.tanle.meeting_room_booking.room.entity.RoomStatus;
import com.tanle.meeting_room_booking.room.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
@Tag(name = "3. Rooms", description = "Quản lý phòng họp và tra cứu phòng trống")
public class RoomController {

    private final RoomService roomService;

    @Operation(
            summary = "Tìm kiếm & phân trang phòng họp",
            description = "Lọc phòng họp theo từ khóa tên phòng, tầng, sức chứa tối thiểu và trạng thái."
    )
    @GetMapping
    public ApiResponse<PageResponse<RoomResponse>> search(
            @Parameter(description = "Từ khóa tên phòng") @RequestParam(required = false) String keyword,
            @Parameter(description = "Tầng") @RequestParam(required = false) Integer floor,
            @Parameter(description = "Sức chứa tối thiểu") @RequestParam(required = false) Integer minCapacity,
            @Parameter(description = "Trạng thái phòng (ACTIVE, MAINTENANCE, INACTIVE)") @RequestParam(required = false) RoomStatus status,
            @Parameter(description = "Số trang (bắt đầu từ 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số bản ghi mỗi trang (tối đa 50)") @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(roomService.search(keyword, floor, minCapacity, status, page, size));
    }

    @Operation(
            summary = "Tra cứu danh sách phòng họp đang trống trong khung giờ",
            description = "Trả về các phòng đang ACTIVE, đủ sức chứa và chưa có lịch đặt phòng (CONFIRMED / IN_PROGRESS) trùng với khoảng `[startTime, endTime)`."
    )
    @GetMapping("/available")
    public ApiResponse<List<RoomResponse>> findAvailable(
            @Parameter(description = "Thời gian bắt đầu (ISO-8601, VD: 2026-10-30T09:00:00+07:00)", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime startTime,
            @Parameter(description = "Thời gian kết thúc (ISO-8601, VD: 2026-10-30T10:00:00+07:00)", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime endTime,
            @Parameter(description = "Sức chứa tối thiểu") @RequestParam(required = false) Integer minCapacity,
            @Parameter(description = "Tầng") @RequestParam(required = false) Integer floor) {
        return ApiResponse.ok(roomService.findAvailableRooms(startTime, endTime, minCapacity, floor));
    }

    @Operation(summary = "Xem chi tiết phòng họp theo ID")
    @GetMapping("/{id}")
    public ApiResponse<RoomResponse> getById(@Parameter(description = "UUID của phòng họp") @PathVariable UUID id) {
        return ApiResponse.ok(roomService.getById(id));
    }

    // ----- Quản trị phòng họp -----

    @Operation(summary = "[Admin/Manager] Tạo phòng họp mới", description = "Yêu cầu quyền `ROOM_CREATE`.")
    @PostMapping
    @PreAuthorize("hasAuthority('ROOM_CREATE')")
    public ResponseEntity<ApiResponse<RoomResponse>> create(@Valid @RequestBody CreateRoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo phòng họp thành công", roomService.createRoom(request)));
    }

    @Operation(summary = "[Admin/Manager] Cập nhật thông tin phòng họp", description = "Yêu cầu quyền `ROOM_UPDATE`.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROOM_UPDATE')")
    public ApiResponse<RoomResponse> update(@Parameter(description = "UUID của phòng họp") @PathVariable UUID id,
                                            @Valid @RequestBody UpdateRoomRequest request) {
        return ApiResponse.ok("Cập nhật phòng họp thành công", roomService.updateRoom(id, request));
    }

    @Operation(summary = "[Admin/Manager] Xóa mềm phòng họp", description = "Yêu cầu quyền `ROOM_DELETE`.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROOM_DELETE')")
    public ApiResponse<Void> delete(@Parameter(description = "UUID của phòng họp") @PathVariable UUID id) {
        roomService.deleteRoom(id);
        return ApiResponse.ok("Xóa phòng họp thành công");
    }
}

