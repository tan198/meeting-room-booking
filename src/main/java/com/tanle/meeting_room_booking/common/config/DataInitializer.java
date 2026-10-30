package com.tanle.meeting_room_booking.common.config;

import com.tanle.meeting_room_booking.announcement.entity.Announcement;
import com.tanle.meeting_room_booking.announcement.entity.AudienceType;
import com.tanle.meeting_room_booking.announcement.repository.AnnouncementRepository;
import com.tanle.meeting_room_booking.booking.entity.*;
import com.tanle.meeting_room_booking.booking.repository.BookingAttendeeRepository;
import com.tanle.meeting_room_booking.booking.repository.BookingAuditLogRepository;
import com.tanle.meeting_room_booking.booking.repository.BookingChangeRepository;
import com.tanle.meeting_room_booking.booking.repository.BookingRepository;
import com.tanle.meeting_room_booking.group.entity.UserGroup;
import com.tanle.meeting_room_booking.group.entity.UserGroupId;
import com.tanle.meeting_room_booking.group.entity.WorkGroup;
import com.tanle.meeting_room_booking.group.repository.UserGroupRepository;
import com.tanle.meeting_room_booking.group.repository.WorkGroupRepository;
import com.tanle.meeting_room_booking.issue.entity.*;
import com.tanle.meeting_room_booking.issue.repository.RoomBlockRepository;
import com.tanle.meeting_room_booking.issue.repository.RoomIssueHistoryRepository;
import com.tanle.meeting_room_booking.issue.repository.RoomIssueRepository;
import com.tanle.meeting_room_booking.notification.service.NotificationService;
import com.tanle.meeting_room_booking.permission.entity.Permission;
import com.tanle.meeting_room_booking.permission.repository.PermissionRepository;
import com.tanle.meeting_room_booking.role.entity.Role;
import com.tanle.meeting_room_booking.role.repository.RoleRepository;
import com.tanle.meeting_room_booking.room.entity.Room;
import com.tanle.meeting_room_booking.room.entity.RoomStatus;
import com.tanle.meeting_room_booking.room.repository.RoomRepository;
import com.tanle.meeting_room_booking.team.entity.Team;
import com.tanle.meeting_room_booking.team.entity.UserTeam;
import com.tanle.meeting_room_booking.team.entity.UserTeamId;
import com.tanle.meeting_room_booking.team.repository.TeamRepository;
import com.tanle.meeting_room_booking.team.repository.UserTeamRepository;
import com.tanle.meeting_room_booking.user.entity.User;
import com.tanle.meeting_room_booking.user.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Khởi tạo dữ liệu nền tảng (Roles, Permissions, 2 tài khoản Admin) và dữ liệu mẫu thực tế (Demo Seed Data)
 * khi ứng dụng khởi động lần đầu (Idempotent - không tạo trùng khi khởi động lại).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final ZoneOffset VN_OFFSET = ZoneOffset.ofHours(7);

    private static final Map<String, String> DEFAULT_ROLES = Map.of(
            "ADMIN", "Quản trị hệ thống",
            "USER", "Người dùng thông thường"
    );

    private static final Map<String, String> DEFAULT_PERMISSIONS;

    static {
        Map<String, String> perms = new LinkedHashMap<>();
        perms.put("USER_READ", "Xem danh sách và chi tiết người dùng");
        perms.put("USER_CREATE", "Tạo người dùng");
        perms.put("USER_UPDATE", "Cập nhật người dùng");
        perms.put("USER_DELETE", "Xóa người dùng");
        perms.put("ROOM_CREATE", "Tạo phòng họp mới");
        perms.put("ROOM_UPDATE", "Cập nhật thông tin và trạng thái phòng họp");
        perms.put("ROOM_DELETE", "Xóa mềm phòng họp");
        perms.put("BOOKING_MANAGE", "Quản lý toàn bộ lịch đặt phòng của hệ thống");
        perms.put("ROLE_MANAGE", "Quản lý vai trò và gán quyền hạn (RBAC)");
        perms.put("TEAM_MANAGE", "Quản lý Team chuyên môn và vị trí thành viên");
        perms.put("GROUP_MANAGE", "Quản lý Group (Phòng ban / Dự án) và phân bổ thành viên");
        DEFAULT_PERMISSIONS = Map.copyOf(perms);
    }

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepo userRepo;
    private final RoomRepository roomRepository;
    private final TeamRepository teamRepository;
    private final UserTeamRepository userTeamRepository;
    private final WorkGroupRepository workGroupRepository;
    private final UserGroupRepository userGroupRepository;
    private final BookingRepository bookingRepository;
    private final BookingAttendeeRepository attendeeRepository;
    private final BookingChangeRepository bookingChangeRepository;
    private final BookingAuditLogRepository auditLogRepository;
    private final RoomIssueRepository roomIssueRepository;
    private final RoomIssueHistoryRepository roomIssueHistoryRepository;
    private final RoomBlockRepository roomBlockRepository;
    private final AnnouncementRepository announcementRepository;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:admin@meetingroom.local}")
    private String adminEmail;

    @Value("${app.admin.secondary-email:admin2@meetingroom.local}")
    private String secondaryAdminEmail;

    @Value("${app.admin.password:Change_me_123!}")
    private String adminPassword;

    @Value("${app.admin.full-name:System Admin}")
    private String adminFullName;

    @Value("${app.demo-seed.enabled:true}")
    private boolean demoSeedEnabled;

    @Override
    @Transactional
    public void run(String... args) {
        seedPermissionsAndRoles();

        Role adminRole = roleRepository.findWithPermissionsByName("ADMIN").orElseThrow();
        Role userRole = roleRepository.findWithPermissionsByName("USER").orElseThrow();

        String rawPwd = (adminPassword == null || adminPassword.isBlank()) ? "Change_me_123!" : adminPassword;

        User admin1 = getOrCreateUser(adminRole, adminEmail, adminFullName, rawPwd);
        User admin2 = getOrCreateUser(adminRole, secondaryAdminEmail, adminFullName + " 2", rawPwd);

        if (!demoSeedEnabled) {
            return;
        }

        seedDemoData(admin1, admin2, userRole, rawPwd);
    }

    private void seedPermissionsAndRoles() {
        DEFAULT_PERMISSIONS.forEach((code, description) -> {
            if (!permissionRepository.existsByCode(code)) {
                permissionRepository.save(Permission.builder().code(code).description(description).build());
            }
        });

        DEFAULT_ROLES.forEach((name, description) -> {
            if (!roleRepository.existsByName(name)) {
                roleRepository.save(Role.builder().name(name).description(description).build());
            }
        });

        Role adminRole = roleRepository.findWithPermissionsByName("ADMIN").orElseThrow();
        Set<Permission> allPerms = Set.copyOf(permissionRepository.findAll());
        if (!adminRole.getPermissions().containsAll(allPerms)) {
            adminRole.getPermissions().addAll(allPerms);
            roleRepository.saveAndFlush(adminRole);
        }

        Role userRole = roleRepository.findWithPermissionsByName("USER").orElseThrow();
        permissionRepository.findByCode("USER_READ").ifPresent(userRead -> {
            if (!userRole.getPermissions().contains(userRead)) {
                userRole.getPermissions().add(userRead);
                roleRepository.saveAndFlush(userRole);
            }
        });
    }

    private void seedDemoData(User admin1, User admin2, Role userRole, String defaultPwd) {
        // 1. Seed 6 tài khoản nhân viên thuộc các vai trò chuyên môn (PM, Backend Dev, Frontend Dev, BA, QA, HR)
        User pmAn = getOrCreateUser(userRole, "pm.an@meetingroom.local", "Lê Hoàng An (PM)", defaultPwd);
        User devBinh = getOrCreateUser(userRole, "dev.binh@meetingroom.local", "Phạm Thanh Bình (Backend Lead)", defaultPwd);
        User devCuong = getOrCreateUser(userRole, "dev.cuong@meetingroom.local", "Vũ Minh Cường (Frontend Dev)", defaultPwd);
        User baDung = getOrCreateUser(userRole, "ba.dung@meetingroom.local", "Đỗ Thu Dung (Business Analyst)", defaultPwd);
        User qaEm = getOrCreateUser(userRole, "qa.em@meetingroom.local", "Hoàng Văn Em (QA/QC)", defaultPwd);
        User hrGiang = getOrCreateUser(userRole, "hr.giang@meetingroom.local", "Ngô Hương Giang (HR)", defaultPwd);

        // 2. Seed 10 Phòng họp trải đều từ tầng 1 đến tầng 5 (đúng bài toán ~10 phòng họp)
        Room p101 = getOrCreateRoom("P101 - Phòng Họp Nhanh Alpha", 1, 4, List.of("TV", "WHITEBOARD", "AIR_CONDITIONER"));
        Room p102 = getOrCreateRoom("P102 - Phòng Tiếp Khách Beta", 1, 6, List.of("TV", "WHITEBOARD", "AIR_CONDITIONER"));
        Room p201 = getOrCreateRoom("P201 - Phòng Kỹ Thuật Gamma", 2, 8, List.of("PROJECTOR", "WHITEBOARD", "AIR_CONDITIONER"));
        Room p202 = getOrCreateRoom("P202 - Phòng Sáng Tạo Delta", 2, 10, List.of("PROJECTOR", "TV", "WHITEBOARD", "AIR_CONDITIONER"));
        Room p301 = getOrCreateRoom("P301 - Phòng Dự Án Epsilon", 3, 12, List.of("PROJECTOR", "VIDEO_CONFERENCE", "WHITEBOARD", "AIR_CONDITIONER"));
        getOrCreateRoom("P302 - Phòng Chiến Lược Zeta", 3, 14, List.of("PROJECTOR", "TV", "VIDEO_CONFERENCE", "WHITEBOARD", "AIR_CONDITIONER"));
        Room p401 = getOrCreateRoom("P401 - Phòng Nhân Sự Eta", 4, 6, List.of("TV", "VIDEO_CONFERENCE", "AIR_CONDITIONER"));
        Room p402 = getOrCreateRoom("P402 - Phòng Đào Tạo Theta", 4, 20, List.of("PROJECTOR", "SOUND_SYSTEM", "WHITEBOARD", "AIR_CONDITIONER"));
        getOrCreateRoom("P501 - Phòng Hội Đồng Iota", 5, 16, List.of("PROJECTOR", "TV", "VIDEO_CONFERENCE", "SOUND_SYSTEM", "AIR_CONDITIONER"));
        getOrCreateRoom("P502 - Hội Trường Lớn Omega", 5, 30, List.of("PROJECTOR", "TV", "VIDEO_CONFERENCE", "SOUND_SYSTEM", "WHITEBOARD", "AIR_CONDITIONER"));

        // 3. Seed các Team chuyên môn (teams & user_teams)
        Team teamBackend = getOrCreateTeam("TEAM_BACKEND", "Team Backend Java/Spring", "Phát triển hệ thống Core API & Database");
        Team teamFrontend = getOrCreateTeam("TEAM_FRONTEND", "Team Frontend Web/Mobile", "Phát triển giao diện Web Portal & App Mobile");
        Team teamBaPm = getOrCreateTeam("TEAM_BA_PM", "Team Quản lý Dự án & BA", "Quản trị tiến độ và phân tích nghiệp vụ");
        Team teamQa = getOrCreateTeam("TEAM_QA", "Team Kiểm thử Chất lượng (QA)", "Kiểm thử tự động và kiểm thử tải");
        Team teamHr = getOrCreateTeam("TEAM_HR", "Team Tuyển dụng & Nhân sự (HR)", "Tuyển dụng, đánh giá nhân sự và vận hành văn phòng");

        ensureTeamMember(teamBackend, devBinh, "LEAD");
        ensureTeamMember(teamBackend, admin1, "MEMBER");
        ensureTeamMember(teamFrontend, devCuong, "LEAD");
        ensureTeamMember(teamBaPm, pmAn, "Project Manager");
        ensureTeamMember(teamBaPm, baDung, "Business Analyst");
        ensureTeamMember(teamQa, qaEm, "LEAD");
        ensureTeamMember(teamHr, hrGiang, "HR Specialist");
        ensureTeamMember(teamHr, admin2, "HR Operations");

        // 4. Seed các Phòng ban (DEPARTMENT) và Dự án (PROJECT) trong work_groups & user_groups
        WorkGroup deptIt = getOrCreateGroup("DEPT_IT", "Phòng Kỹ thuật & Công nghệ", "DEPARTMENT", "Đơn vị phát triển và vận hành hạ tầng CNTT");
        WorkGroup deptProduct = getOrCreateGroup("DEPT_PRODUCT", "Phòng Sản phẩm & Nghiệp vụ", "DEPARTMENT", "Đơn vị quản lý sản phẩm và phân tích nghiệp vụ");
        WorkGroup deptHr = getOrCreateGroup("DEPT_HR", "Phòng Nhân sự & Hành chính", "DEPARTMENT", "Đơn vị quản trị nguồn nhân lực và hành chính nội bộ");

        WorkGroup projErp = getOrCreateGroup("PROJ_ERP", "Dự án Nâng cấp ERP Core 2026", "PROJECT", "Dự án trọng điểm chuyển đổi hệ thống ERP toàn công ty");
        WorkGroup projSuperApp = getOrCreateGroup("PROJ_SUPERAPP", "Dự án SuperApp Nội bộ", "PROJECT", "Ứng dụng di động tích hợp đặt phòng họp và chấm công");

        ensureGroupMember(deptIt, devBinh, "HEAD");
        ensureGroupMember(deptIt, devCuong, "MEMBER");
        ensureGroupMember(deptIt, qaEm, "MEMBER");
        ensureGroupMember(deptIt, admin1, "MEMBER");

        ensureGroupMember(deptProduct, pmAn, "HEAD");
        ensureGroupMember(deptProduct, baDung, "MEMBER");

        ensureGroupMember(deptHr, hrGiang, "HEAD");
        ensureGroupMember(deptHr, admin2, "MEMBER");

        ensureGroupMember(projErp, pmAn, "PM");
        ensureGroupMember(projErp, devBinh, "Backend Lead");
        ensureGroupMember(projErp, baDung, "BA");
        ensureGroupMember(projErp, qaEm, "QA");

        ensureGroupMember(projSuperApp, pmAn, "PM");
        ensureGroupMember(projSuperApp, devCuong, "Frontend Lead");
        ensureGroupMember(projSuperApp, qaEm, "QA");

        // 5. Chỉ seed Bookings, BookingChanges, RoomIssues, RoomBlocks và Announcements nếu bảng bookings đang trống
        if (bookingRepository.count() > 0) {
            return;
        }

        LocalDate today = LocalDate.now(VN_OFFSET);
        LocalDate yesterday = today.minusDays(1);
        LocalDate tomorrow = today.plusDays(1);

        // 5.1. [PENDING] Yêu cầu đặt phòng đang chờ Admin duyệt (vào ngày mai 09:00 - 10:30)
        Booking pendingBooking = bookingRepository.saveAndFlush(Booking.builder()
                .preferredRoom(p301)
                .organizer(pmAn)
                .organizerDepartmentId(deptProduct.getId())
                .relatedDepartmentIds(List.of(deptProduct.getId(), deptIt.getId()))
                .team(teamBaPm)
                .group(projErp)
                .title("Họp Sprint Planning Dự án ERP - Sprint 12")
                .description("Thống nhất backlog, phân bổ task Backend/BA/QA cho Sprint 12")
                .note("Cần phòng có Video Conference và máy chiếu")
                .attendeeCount(6)
                .meetingType(MeetingType.PLANNING)
                .visibility(Visibility.STANDARD)
                .confidential(false)
                .startTime(atVn(tomorrow, 9, 0))
                .endTime(atVn(tomorrow, 10, 30))
                .status(BookingStatus.PENDING)
                .usageStatus(UsageStatus.NOT_APPLICABLE)
                .build());
        saveAttendees(pendingBooking, pmAn, List.of(devBinh, baDung, qaEm));
        saveAudit(pendingBooking.getId(), pmAn.getId(), "SUBMIT_REQUEST", "Gửi yêu cầu đặt phòng mẫu (PENDING)");

        // 5.2. [APPROVED + NOT_CHECKED_IN] Lịch đã được Admin 2 duyệt tại phòng P202 (ngày mai 14:00 - 15:30)
        //      kèm 1 đề nghị thay đổi (BookingChange PENDING) xin đổi sang 15:30 - 17:00 tại phòng P301
        OffsetDateTime approved2Start = atVn(tomorrow, 14, 0);
        OffsetDateTime approved2End = atVn(tomorrow, 15, 30);
        Booking approvedBooking = bookingRepository.saveAndFlush(Booking.builder()
                .room(p202)
                .preferredRoom(p202)
                .organizer(devBinh)
                .organizerDepartmentId(deptIt.getId())
                .relatedDepartmentIds(List.of(deptIt.getId(), deptProduct.getId()))
                .team(teamBackend)
                .group(projErp)
                .title("Review Thiết kế Kiến trúc Database & Locking")
                .description("Rà soát chiến lược GiST Exclusion Constraint và Pessimistic Locking")
                .attendeeCount(5)
                .meetingType(MeetingType.REVIEW)
                .visibility(Visibility.STANDARD)
                .confidential(false)
                .startTime(approved2Start)
                .endTime(approved2End)
                .status(BookingStatus.APPROVED)
                .usageStatus(UsageStatus.NOT_CHECKED_IN)
                .checkInDeadline(approved2Start.plusMinutes(10))
                .approvedBy(admin2.getId())
                .approvedAt(OffsetDateTime.now().minusHours(2))
                .approvalNote("Đã bố trí phòng P202 tầng 2 đủ 10 chỗ")
                .build());
        saveAttendees(approvedBooking, devBinh, List.of(pmAn, devCuong, qaEm));
        saveAudit(approvedBooking.getId(), admin2.getId(), "APPROVE_BOOKING", "Phê duyệt và gán phòng P202");

        bookingChangeRepository.saveAndFlush(BookingChange.builder()
                .booking(approvedBooking)
                .baseVersion(approvedBooking.getVersion())
                .title("Review Thiết kế Kiến trúc Database & Locking (Mở rộng)")
                .purpose("Rà soát thêm kịch bản нагрузочное тестирование và backup recovery")
                .note("Xin lùi xuống 15:30 và đổi sang phòng P301 rộng hơn")
                .meetingType(MeetingType.REVIEW)
                .visibility(Visibility.STANDARD)
                .startTime(atVn(tomorrow, 15, 30))
                .endTime(atVn(tomorrow, 17, 0))
                .attendeeCount(8)
                .preferredRoom(p301)
                .team(teamBackend)
                .group(projErp)
                .relatedDepartmentIds(List.of(deptIt.getId(), deptProduct.getId()))
                .participantIds(List.of(devBinh.getId(), pmAn.getId(), devCuong.getId(), qaEm.getId(), baDung.getId()))
                .status(BookingChangeStatus.PENDING)
                .build());

        // 5.3. [APPROVED + CONFIDENTIAL] Cuộc họp phỏng vấn ứng viên (INTERVIEW - bắt buộc CONFIDENTIAL)
        //      vào ngày mai 10:00 - 11:30 tại phòng P401 (để test che thông tin riêng tư AC-71..AC-76)
        OffsetDateTime interviewStart = atVn(tomorrow, 10, 0);
        OffsetDateTime interviewEnd = atVn(tomorrow, 11, 30);
        Booking confidentialInterview = bookingRepository.saveAndFlush(Booking.builder()
                .room(p401)
                .preferredRoom(p401)
                .organizer(hrGiang)
                .organizerDepartmentId(deptHr.getId())
                .relatedDepartmentIds(List.of(deptHr.getId(), deptIt.getId()))
                .team(teamHr)
                .title("Phỏng vấn Senior Java Backend (Ứng viên Nguyễn Văn X)")
                .description("Phỏng vấn chuyên sâu Java 21, Spring Boot 3 và PostgreSQL")
                .note("Có 1 ứng viên bên ngoài tham dự")
                .attendeeCount(3) // 2 nhân sự nội bộ (HR + Backend Lead) + 1 ứng viên ngoài
                .meetingType(MeetingType.INTERVIEW)
                .visibility(Visibility.CONFIDENTIAL)
                .confidential(true)
                .startTime(interviewStart)
                .endTime(interviewEnd)
                .status(BookingStatus.APPROVED)
                .usageStatus(UsageStatus.NOT_CHECKED_IN)
                .checkInDeadline(interviewStart.plusMinutes(10))
                .approvedBy(admin1.getId())
                .approvedAt(OffsetDateTime.now().minusHours(3))
                .approvalNote("Bố trí phòng P401 tầng 4 cho buổi phỏng vấn")
                .build());
        saveAttendees(confidentialInterview, hrGiang, List.of(devBinh));
        saveAudit(confidentialInterview.getId(), admin1.getId(), "APPROVE_BOOKING", "Phê duyệt phòng P401");

        // 5.4. [APPROVED + COMPLETED (USER_CHECKOUT trả phòng sớm)] Ngày hôm qua 09:00 - 10:00, check-in 09:05, check-out sớm 09:40 (AC-64)
        OffsetDateTime y1Start = atVn(yesterday, 9, 0);
        OffsetDateTime y1End = atVn(yesterday, 10, 0);
        OffsetDateTime y1CheckIn = atVn(yesterday, 9, 5);
        OffsetDateTime y1CheckOut = atVn(yesterday, 9, 40);
        Booking completedConfirmed = bookingRepository.saveAndFlush(Booking.builder()
                .room(p201)
                .preferredRoom(p201)
                .organizer(devCuong)
                .organizerDepartmentId(deptIt.getId())
                .relatedDepartmentIds(List.of(deptIt.getId()))
                .team(teamFrontend)
                .group(projSuperApp)
                .title("Đồng bộ Giao diện Đặt phòng trên SuperApp Mobile")
                .description("Chốt luồng UI/UX màn hình lịch biểu và thông báo")
                .attendeeCount(4)
                .meetingType(MeetingType.SYNC)
                .visibility(Visibility.STANDARD)
                .confidential(false)
                .startTime(y1Start)
                .endTime(y1End)
                .actualStartTime(y1CheckIn)
                .actualEndTime(y1CheckOut)
                .releasedAt(y1CheckOut)
                .status(BookingStatus.APPROVED)
                .usageStatus(UsageStatus.COMPLETED)
                .checkInDeadline(y1Start.plusMinutes(10))
                .checkedInBy(devCuong.getId())
                .checkedOutBy(devCuong.getId())
                .evidenceType("USER_CONFIRMATION")
                .endSource("USER_CHECKOUT")
                .approvedBy(admin1.getId())
                .approvedAt(y1Start.minusDays(1))
                .build());
        saveAttendees(completedConfirmed, devCuong, List.of(pmAn, qaEm));

        // 5.5. [APPROVED + COMPLETED (SYSTEM_SCHEDULED_END kết thúc suy định)] Ngày hôm qua 14:00 - 16:00, check-in 14:05, quên check-out (AC-65)
        OffsetDateTime y2Start = atVn(yesterday, 14, 0);
        OffsetDateTime y2End = atVn(yesterday, 16, 0);
        Booking completedInferred = bookingRepository.saveAndFlush(Booking.builder()
                .room(p402)
                .preferredRoom(p402)
                .organizer(devBinh)
                .organizerDepartmentId(deptIt.getId())
                .relatedDepartmentIds(List.of(deptIt.getId(), deptProduct.getId()))
                .team(teamBackend)
                .title("Đào tạo Nội bộ Bảo mật API & Phòng chống Leo thang Đặc quyền")
                .description("Chia sẻ kinh nghiệm thiết kế RBAC và kiểm soát giao dịch đồng thời")
                .attendeeCount(12)
                .meetingType(MeetingType.TRAINING)
                .visibility(Visibility.STANDARD)
                .confidential(false)
                .startTime(y2Start)
                .endTime(y2End)
                .actualStartTime(atVn(yesterday, 14, 5))
                .status(BookingStatus.APPROVED)
                .usageStatus(UsageStatus.COMPLETED)
                .checkInDeadline(y2Start.plusMinutes(10))
                .checkedInBy(devBinh.getId())
                .evidenceType("USER_CONFIRMATION")
                .endSource("SYSTEM_SCHEDULED_END")
                .preEndNotified(true)
                .approvedBy(admin2.getId())
                .approvedAt(y2Start.minusDays(1))
                .build());
        saveAttendees(completedInferred, devBinh, List.of(devCuong, pmAn, baDung, qaEm));

        // 5.6. [CANCELLED + NO_SHOW] Ngày hôm qua 10:30 - 11:30, quá hạn 10:40 không check-in (AC-50 & AC-66)
        OffsetDateTime y3Start = atVn(yesterday, 10, 30);
        OffsetDateTime y3End = atVn(yesterday, 11, 30);
        OffsetDateTime y3Deadline = y3Start.plusMinutes(10);
        Booking noShowBooking = bookingRepository.saveAndFlush(Booking.builder()
                .room(p102)
                .preferredRoom(p102)
                .organizer(qaEm)
                .organizerDepartmentId(deptIt.getId())
                .relatedDepartmentIds(List.of(deptIt.getId()))
                .team(teamQa)
                .group(projErp)
                .title("Rà soát Kịch bản Regression Test")
                .description("Kiểm tra bộ test tự động trước khi đóng bản build")
                .attendeeCount(3)
                .meetingType(MeetingType.GENERAL)
                .visibility(Visibility.STANDARD)
                .confidential(false)
                .startTime(y3Start)
                .endTime(y3End)
                .status(BookingStatus.CANCELLED)
                .usageStatus(UsageStatus.NO_SHOW)
                .checkInDeadline(y3Deadline)
                .cancellationReasonCode("NO_SHOW")
                .cancelReason("Tự động hủy do quá thời hạn check-in (NO_SHOW)")
                .cancelledAt(y3Deadline)
                .releasedAt(y3Deadline)
                .approvedBy(admin1.getId())
                .approvedAt(y3Start.minusDays(1))
                .build());
        saveAttendees(noShowBooking, qaEm, List.of(baDung));

        // 6. Seed 1 Báo cáo sự cố phòng họp (RoomIssue OPEN)
        RoomIssue issue = roomIssueRepository.saveAndFlush(RoomIssue.builder()
                .room(p201)
                .reporter(devCuong)
                .relatedBookingId(completedConfirmed.getId())
                .category(IssueCategory.EQUIPMENT)
                .title("Cáp HDMI bàn họp phòng P201 bị chập chờn")
                .description("Khi cắm vào laptop bị mất tín hiệu hình ảnh khoảng 2-3 giây/lần, cần thay dây HDMI mới.")
                .proposedImpact(IssueImpact.DEGRADED)
                .status(IssueStatus.OPEN)
                .build());
        roomIssueHistoryRepository.save(RoomIssueHistory.builder()
                .issueId(issue.getId())
                .actorId(devCuong.getId())
                .fromStatus(null)
                .toStatus(IssueStatus.OPEN)
                .reason("Khởi tạo báo cáo sự cố mẫu")
                .build());

        // 7. Seed 1 Khoảng chặn phòng bảo trì định kỳ (RoomBlock ACTIVE) tại phòng P101 vào chiều mai 16:00 - 17:30
        roomBlockRepository.saveAndFlush(RoomBlock.builder()
                .room(p101)
                .startTime(atVn(tomorrow, 16, 0))
                .endTime(atVn(tomorrow, 17, 30))
                .reason("Bảo trì vệ sinh định kỳ hệ thống điều hòa phòng P101")
                .status(RoomBlockStatus.ACTIVE)
                .createdBy(admin1.getId())
                .build());

        // 8. Seed 1 Thông báo chung toàn công ty (Announcement) + fan-out vào hộp thư Notification của từng nhân viên
        List<UUID> allUserIds = userRepo.findAllActiveUserIds();
        Announcement announcement = announcementRepository.saveAndFlush(Announcement.builder()
                .title("Chào mừng vận hành Hệ thống Đặt phòng họp Nội bộ mới")
                .content("Hệ thống hỗ trợ đặt phòng theo Team/Dự án, phê duyệt độc lập, đề nghị đổi lịch an toàn (BookingChange) và check-in/check-out giải phóng phòng sớm.")
                .audienceType(AudienceType.ALL)
                .audienceIds(List.of())
                .recipientIds(allUserIds)
                .recipientCount(allUserIds.size())
                .publishedBy(admin1.getId())
                .publishedAt(OffsetDateTime.now())
                .build());

        notificationService.sendBulk(
                new LinkedHashSet<>(allUserIds),
                null,
                "ANNOUNCEMENT",
                announcement.getTitle(),
                announcement.getContent(),
                Map.of("announcementId", announcement.getId().toString(), "audienceType", "ALL"),
                "ANNOUNCEMENT:" + announcement.getId()
        );

        log.info("[DataInitializer] Đã khởi tạo thành công toàn bộ dữ liệu mẫu (8 Users, 10 Rooms, 5 Teams, 5 Groups/Projects, 6 Bookings, 1 BookingChange, 1 RoomIssue, 1 RoomBlock, 1 Announcement)!");
    }

    private User getOrCreateUser(Role role, String rawEmail, String fullName, String rawPassword) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        return userRepo.findByEmail(email).orElseGet(() -> {
            User saved = userRepo.saveAndFlush(User.builder()
                    .email(email)
                    .password(passwordEncoder.encode(rawPassword))
                    .fullName(fullName)
                    .role(role)
                    .isActive(true)
                    .build());
            log.info("Đã tạo tài khoản mẫu: {} ({})", email, role.getName());
            return saved;
        });
    }

    private Room getOrCreateRoom(String name, int floor, int capacity, List<String> facilities) {
        return roomRepository.findAll().stream()
                .filter(r -> r.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElseGet(() -> roomRepository.saveAndFlush(Room.builder()
                        .name(name)
                        .floor(floor)
                        .capacity(capacity)
                        .facilities(new ArrayList<>(facilities))
                        .status(RoomStatus.ACTIVE)
                        .build()));
    }

    private Team getOrCreateTeam(String code, String name, String description) {
        return teamRepository.findByCodeIgnoreCase(code).orElseGet(() ->
                teamRepository.saveAndFlush(Team.builder()
                        .code(code)
                        .name(name)
                        .type("TEAM")
                        .description(description)
                        .build())
        );
    }

    private void ensureTeamMember(Team team, User user, String memberRole) {
        UserTeamId id = new UserTeamId(user.getId(), team.getId());
        if (!userTeamRepository.existsById(id)) {
            userTeamRepository.saveAndFlush(UserTeam.builder()
                    .id(id)
                    .user(user)
                    .team(team)
                    .memberRole(memberRole)
                    .build());
        }
    }

    private WorkGroup getOrCreateGroup(String code, String name, String type, String description) {
        return workGroupRepository.findAll().stream()
                .filter(g -> g.getCode().equalsIgnoreCase(code))
                .findFirst()
                .orElseGet(() -> workGroupRepository.saveAndFlush(WorkGroup.builder()
                        .code(code)
                        .name(name)
                        .type(type)
                        .description(description)
                        .build()));
    }

    private void ensureGroupMember(WorkGroup group, User user, String memberRole) {
        UserGroupId id = new UserGroupId(user.getId(), group.getId());
        if (!userGroupRepository.existsById(id)) {
            userGroupRepository.saveAndFlush(UserGroup.builder()
                    .id(id)
                    .user(user)
                    .group(group)
                    .build());
        }
    }

    private void saveAttendees(Booking booking, User organizer, List<User> participants) {
        OffsetDateTime now = OffsetDateTime.now();
        attendeeRepository.save(BookingAttendee.builder()
                .id(new BookingAttendeeId(booking.getId(), organizer.getId()))
                .booking(booking)
                .user(organizer)
                .attendeeRole(AttendeeRole.ORGANIZER)
                .rsvpStatus(RsvpStatus.ACCEPTED)
                .respondedAt(now)
                .build());

        for (User p : participants) {
            if (!p.getId().equals(organizer.getId())) {
                attendeeRepository.save(BookingAttendee.builder()
                        .id(new BookingAttendeeId(booking.getId(), p.getId()))
                        .booking(booking)
                        .user(p)
                        .attendeeRole(AttendeeRole.REQUIRED)
                        .rsvpStatus(RsvpStatus.ACCEPTED)
                        .respondedAt(now)
                        .build());
            }
        }
    }

    private void saveAudit(UUID bookingId, UUID actorId, String action, String reason) {
        auditLogRepository.save(BookingAuditLog.builder()
                .bookingId(bookingId)
                .actorId(actorId)
                .action(action)
                .reason(reason)
                .details(Map.of("seeded", true))
                .build());
    }

    private OffsetDateTime atVn(LocalDate date, int hour, int minute) {
        return OffsetDateTime.of(date, LocalTime.of(hour, minute), VN_OFFSET);
    }
}
