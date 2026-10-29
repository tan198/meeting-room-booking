package com.tanle.meeting_room_booking.team.controller;

import com.tanle.meeting_room_booking.common.response.ApiResponse;
import com.tanle.meeting_room_booking.team.dto.AddTeamMemberRequest;
import com.tanle.meeting_room_booking.team.dto.CreateTeamRequest;
import com.tanle.meeting_room_booking.team.dto.TeamResponse;
import com.tanle.meeting_room_booking.team.service.TeamService;
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
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
@Tag(name = "6. Teams", description = "Do Admin tạo (VD: Team Dev, Team BA, Team PM...) để gắn nhân viên khi tạo User và hỗ trợ chọn thành viên khi đặt lịch họp theo Team")
public class TeamController {

    private final TeamService teamService;

    @Operation(summary = "Danh sách toàn bộ Team kèm danh sách thành viên")
    @GetMapping
    public ApiResponse<List<TeamResponse>> getAll() {
        return ApiResponse.ok(teamService.findAll());
    }

    @Operation(summary = "Xem chi tiết Team theo ID")
    @GetMapping("/{id}")
    public ApiResponse<TeamResponse> getById(@Parameter(description = "UUID của team") @PathVariable UUID id) {
        return ApiResponse.ok(teamService.getById(id));
    }

    @Operation(
            summary = "Lấy danh sách thành viên thuộc một Team (để người tạo cuộc họp chọn người liên quan)",
            description = "Khi người tạo cuộc họp chọn họp theo Team, gọi API này để hiển thị danh sách thành viên trong Team đó và tích chọn những người cần tham gia."
    )
    @GetMapping("/{id}/members")
    public ApiResponse<List<TeamResponse.TeamMemberDto>> getMembers(
            @Parameter(description = "UUID của team") @PathVariable UUID id) {
        return ApiResponse.ok(teamService.getMembers(id));
    }

    @Operation(summary = "[Admin] Tạo Team mới (VD: Team Dev, Team PM, Team BA...)", description = "Yêu cầu quyền `TEAM_MANAGE`.")
    @PostMapping
    @PreAuthorize("hasAuthority('TEAM_MANAGE')")
    public ResponseEntity<ApiResponse<TeamResponse>> create(@Valid @RequestBody CreateTeamRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Tạo Team thành công", teamService.createTeam(request)));
    }

    @Operation(
            summary = "[Admin] Thêm hoặc cập nhật thành viên trong Team",
            description = "Yêu cầu quyền `TEAM_MANAGE`."
    )
    @PostMapping("/{id}/members")
    @PreAuthorize("hasAuthority('TEAM_MANAGE')")
    public ApiResponse<TeamResponse> addMember(
            @Parameter(description = "UUID của team") @PathVariable UUID id,
            @Valid @RequestBody AddTeamMemberRequest request) {
        return ApiResponse.ok("Cập nhật thành viên vào Team thành công", teamService.addMember(id, request));
    }

    @Operation(summary = "[Admin] Xóa thành viên khỏi Team", description = "Yêu cầu quyền `TEAM_MANAGE`.")
    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasAuthority('TEAM_MANAGE')")
    public ApiResponse<Void> removeMember(
            @Parameter(description = "UUID của team") @PathVariable UUID id,
            @Parameter(description = "UUID của người dùng") @PathVariable UUID userId) {
        teamService.removeMember(id, userId);
        return ApiResponse.ok("Đã xóa thành viên khỏi Team");
    }
}
