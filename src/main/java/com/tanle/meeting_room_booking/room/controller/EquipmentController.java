package com.tanle.meeting_room_booking.room.controller;

import com.tanle.meeting_room_booking.common.response.ApiResponse;
import com.tanle.meeting_room_booking.room.dto.*;
import com.tanle.meeting_room_booking.room.entity.RoomStatus;
import com.tanle.meeting_room_booking.room.service.EquipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "3.1 Equipments", description = "Danh mục thiết bị phòng họp và cấu hình thiết bị theo phòng (Spec mục 6.2, 16 & 21.4)")
public class EquipmentController {

    private final EquipmentService equipmentService;

    @Operation(summary = "Danh sách danh mục thiết bị phòng họp (hỗ trợ cả /api/v1/equipment và /api/v1/equipments)")
    @GetMapping({"/equipment", "/equipments"})
    public ApiResponse<List<EquipmentResponse>> getAll(
            @Parameter(description = "Lọc theo trạng thái (`ACTIVE`, `INACTIVE`)") @RequestParam(required = false) RoomStatus status) {
        return ApiResponse.ok(equipmentService.findAll(status));
    }

    @Operation(summary = "Chi tiết danh mục thiết bị theo ID")
    @GetMapping({"/equipment/{id}", "/equipments/{id}"})
    public ApiResponse<EquipmentResponse> getById(@Parameter(description = "UUID của thiết bị") @PathVariable UUID id) {
        return ApiResponse.ok(equipmentService.getById(id));
    }

    @Operation(
            summary = "[Admin] Tạo mới danh mục thiết bị phòng họp (Spec 21.4)",
            description = "Yêu cầu quyền `ROOM_CREATE` hoặc `ROOM_UPDATE`. Mã thiết bị (`code`) tự chuẩn hóa chữ hoa và duy nhất."
    )
    @PostMapping({"/equipment", "/equipments"})
    @PreAuthorize("hasAuthority('ROOM_CREATE') or hasAuthority('ROOM_UPDATE')")
    public ResponseEntity<ApiResponse<EquipmentResponse>> create(@Valid @RequestBody CreateEquipmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo danh mục thiết bị thành công", equipmentService.createEquipment(request)));
    }

    @Operation(
            summary = "[Admin] Cập nhật thông tin hoặc trạng thái thiết bị (Spec 21.4 & AC-39)",
            description = "Yêu cầu quyền `ROOM_UPDATE`. Ngừng hoạt động thiết bị (`INACTIVE`) sẽ bị chặn (`ACTIVE_DEPENDENCIES_EXIST`) nếu còn lịch họp tương lai phụ thuộc."
    )
    @RequestMapping(value = {"/equipment/{id}", "/equipments/{id}"}, method = {RequestMethod.PATCH, RequestMethod.PUT})
    @PreAuthorize("hasAuthority('ROOM_UPDATE')")
    public ApiResponse<EquipmentResponse> update(
            @Parameter(description = "UUID của thiết bị") @PathVariable UUID id,
            @Valid @RequestBody UpdateEquipmentRequest request) {
        return ApiResponse.ok("Cập nhật thiết bị thành công", equipmentService.updateEquipment(id, request));
    }

    @Operation(
            summary = "[Admin] Cập nhật danh sách thiết bị gắn với một phòng họp (PUT /api/v1/rooms/{id}/equipment - Spec 16)",
            description = "Yêu cầu quyền `ROOM_UPDATE`."
    )
    @PutMapping("/rooms/{id}/equipment")
    @PreAuthorize("hasAuthority('ROOM_UPDATE')")
    public ApiResponse<RoomResponse> updateRoomEquipment(
            @Parameter(description = "UUID của phòng họp") @PathVariable UUID id,
            @Valid @RequestBody UpdateRoomEquipmentRequest request) {
        return ApiResponse.ok("Cập nhật thiết bị của phòng thành công", equipmentService.updateRoomEquipment(id, request.equipments()));
    }
}

