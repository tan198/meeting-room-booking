package com.tanle.meeting_room_booking.issue.controller;

import com.tanle.meeting_room_booking.common.response.ApiResponse;
import com.tanle.meeting_room_booking.common.response.PageResponse;
import com.tanle.meeting_room_booking.common.security.AuthUser;
import com.tanle.meeting_room_booking.issue.dto.*;
import com.tanle.meeting_room_booking.issue.entity.RoomBlockStatus;
import com.tanle.meeting_room_booking.issue.service.RoomBlockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/room-blocks")
@RequiredArgsConstructor
@Tag(name = "9. Room Blocks", description = "Chặn phòng theo khoảng thời gian để bảo trì/sự cố kèm quy trình Preview và xử lý nguyên tử các lịch bị ảnh hưởng (Spec mục 11)")
public class RoomBlockController {

    private final RoomBlockService roomBlockService;

    @Operation(
            summary = "[Admin] Xem trước tác động khi dự kiến chặn phòng (Spec 11.2)",
            description = "Yêu cầu quyền `ROOM_UPDATE` hoặc `BOOKING_MANAGE`. Trả về danh sách các lịch `APPROVED` bị ảnh hưởng trong khoảng chặn kèm gợi ý các phòng thay thế khả dụng."
    )
    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('ROOM_UPDATE') or hasAuthority('BOOKING_MANAGE')")
    public ApiResponse<RoomBlockPreviewResponse> preview(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Valid @RequestBody PreviewRoomBlockRequest request) {
        return ApiResponse.ok(roomBlockService.previewBlock(principal, request));
    }

    @Operation(
            summary = "[Admin] Tạo khoảng chặn phòng nguyên tử kèm phương án xử lý toàn bộ lịch bị ảnh hưởng (Spec 11.2 & AC-21..AC-23)",
            description = """
                    Yêu cầu quyền `ROOM_UPDATE` hoặc `BOOKING_MANAGE`:
                    - Bắt buộc xử lý hết các booking đang chiếm chỗ thông qua danh sách `resolutions` (`REASSIGN` hoặc `CANCEL`).
                    - Nếu thiếu booking bị ảnh hưởng -> trả `409 IMPACT_PLAN_INCOMPLETE`.
                    - Nếu phát sinh booking mới hoặc lệch `expectedVersion` từ lúc preview -> trả `409 IMPACT_PLAN_STALE`.
                    - Toàn bộ chuyển phòng/hủy lịch và lưu `RoomBlock` diễn ra nguyên tử trong một giao dịch DB.
                    """
    )
    @PostMapping
    @PreAuthorize("hasAuthority('ROOM_UPDATE') or hasAuthority('BOOKING_MANAGE')")
    public ResponseEntity<ApiResponse<RoomBlockResponse>> create(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Valid @RequestBody CreateRoomBlockRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo khoảng chặn phòng và xử lý lịch bị ảnh hưởng thành công", roomBlockService.createBlock(principal, request)));
    }

    @Operation(summary = "Danh sách khoảng chặn phòng (RoomBlock)")
    @GetMapping
    public ApiResponse<PageResponse<RoomBlockResponse>> search(
            @Parameter(description = "Lọc theo UUID phòng họp") @RequestParam(required = false) UUID roomId,
            @Parameter(description = "Lọc theo trạng thái (`ACTIVE`, `CANCELLED`)") @RequestParam(required = false) RoomBlockStatus status,
            @Parameter(description = "Số trang (bắt đầu từ 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số bản ghi mỗi trang (tối đa 100)") @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(roomBlockService.search(roomId, status, page, size));
    }

    @Operation(summary = "Xem chi tiết khoảng chặn phòng")
    @GetMapping("/{id}")
    public ApiResponse<RoomBlockResponse> getById(@Parameter(description = "UUID của RoomBlock") @PathVariable UUID id) {
        return ApiResponse.ok(roomBlockService.getById(id));
    }

    @Operation(
            summary = "[Admin] Hủy khoảng chặn phòng (Spec 11.3 & AC-24)",
            description = "Yêu cầu quyền `ROOM_UPDATE` hoặc `BOOKING_MANAGE`. Mở lại khả năng phân phòng trong khoảng đó; không tự khôi phục các booking đã hủy hoặc đã chuyển phòng."
    )
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('ROOM_UPDATE') or hasAuthority('BOOKING_MANAGE')")
    public ApiResponse<RoomBlockResponse> cancel(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "UUID của RoomBlock") @PathVariable UUID id,
            @Valid @RequestBody CancelRoomBlockRequest request) {
        return ApiResponse.ok("Đã hủy khoảng chặn phòng", roomBlockService.cancelBlock(principal, id, request));
    }

    @Operation(
            summary = "[Admin] Thay thế khoảng chặn phòng hiện có bằng khoảng chặn mới nguyên tử (Spec 11.3)",
            description = "Yêu cầu quyền `ROOM_UPDATE` hoặc `BOOKING_MANAGE`. Hủy RoomBlock cũ và tạo RoomBlock mới kèm phương án xử lý lịch bị ảnh hưởng trong cùng một giao dịch."
    )
    @PostMapping("/{id}/replace")
    @PreAuthorize("hasAuthority('ROOM_UPDATE') or hasAuthority('BOOKING_MANAGE')")
    public ApiResponse<RoomBlockResponse> replace(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthUser principal,
            @Parameter(description = "UUID của RoomBlock cần thay thế") @PathVariable UUID id,
            @Valid @RequestBody CreateRoomBlockRequest request) {
        return ApiResponse.ok("Đã thay thế khoảng chặn phòng thành công", roomBlockService.replaceBlock(principal, id, request));
    }
}

