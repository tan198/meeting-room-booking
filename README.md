# Meeting Room Booking System — Backend REST API

Hệ thống Backend REST API quản lý và đặt phòng họp nội bộ cho doanh nghiệp (~10 phòng họp, ~200 nhân viên), giải quyết triệt để tình trạng đặt trùng lịch phòng, trùng lịch cá nhân khi mời họp đồng thời, giữ phòng ảo (ghost meetings) và quản lý linh hoạt theo Team / Dự án.

---

## 1. Công nghệ sử dụng (Tech Stack)

- **Ngôn ngữ & Framework**: Java 21, Spring Boot 3.5, Spring Security 6, Spring Data JPA (Hibernate 6).
- **Cơ sở dữ liệu & Migration**: PostgreSQL 17 (`btree_gist`, `JSONB`, Partial Indexes, Row-level Locking), Flyway (`V1` -> `V10`).
- **Bảo mật**: Stateless JWT (`HS256` với `nimbus-jose-jwt`) kết hợp Opaque Refresh Token Rotation (`SHA-256`), Dynamic RBAC (Role-Permission động) và kiểm soát chống leo thang đặc quyền (Privilege Escalation Protection).
- **Tài liệu API**: OpenAPI 3 / Swagger UI (`springdoc-openapi`).
- **Kiểm thử & Đóng gói**: JUnit 5, Testcontainers (PostgreSQL 17 thực tế), Docker & Docker Compose.

---

## 2. Cách chạy dự án (Quick Start)

### Cách 1: Chạy toàn bộ bằng Docker Compose (Khuyên dùng)

Dự án đã được cấu hình sẵn multi-stage `Dockerfile` và `docker-compose.yml` (tự động khởi tạo PostgreSQL 17, chạy toàn bộ Flyway migrations từ `V1` đến `V10` và khởi tạo tài khoản Admin mặc định).

```bash
# 1. Tạo file .env từ .env.example (nếu chưa có)
cp .env.example .env

# 2. Khởi chạy toàn bộ hệ thống (PostgreSQL + Backend API)
docker compose up --build -d
```

Sau khi container khởi động hoàn tất:
- **API Base URL**: `http://localhost:8000`
- **Swagger UI (Tài liệu & Test trực tiếp)**: `http://localhost:8000/swagger-ui.html`
- **Health Check**: `http://localhost:8000/actuator/health`
- **Tài khoản Admin mặc định** (cấu hình trong `.env`):
  - Email: `admin@meetingroom.local`
  - Password: `Change_me_123!`

### Cách 2: Chạy trực tiếp trên máy Local (IDE / Maven)

1. Khởi chạy PostgreSQL (có thể dùng `docker compose up -d postgres`).
2. Cấu hình thông số kết nối trong file `.env` (Spring Boot tự động nạp qua `spring.config.import=optional:file:.env[.properties]`).
3. Chạy ứng dụng:
   ```bash
   ./mvnw spring-boot:run
   ```

### Chạy kiểm thử tự động (Unit & Integration Tests với Testcontainers)
```bash
./mvnw test
```

---

## 3. Phân tích nghiệp vụ & Kiến trúc thiết kế

### 3.1. Phân tầng mức độ ưu tiên tính năng (P0 -> P1 -> P2)
Để hệ thống vừa giải quyết triệt để bài toán cốt lõi, vừa sẵn sàng mở rộng cho môi trường thực tế tại doanh nghiệp 200 nhân sự, schema và nghiệp vụ được chia làm 3 lớp:

1. **Lõi đặt phòng — P0 (`V1`..`V5`)**:
   - Quản lý người dùng, phân quyền động RBAC (`Role` - `Permission`), quản lý phòng họp (`ACTIVE`, `MAINTENANCE`, `INACTIVE`, tiện ích `facilities` dạng `JSONB`).
   - Đặt phòng, tìm phòng trống theo khung giờ & sức chứa, xem lịch phòng, cập nhật/hủy lịch.
   - Vòng đời thực tế của cuộc họp: `CONFIRMED` -> `IN_PROGRESS` (Check-in) -> `COMPLETED` (Check-out) hoặc `NO_SHOW` (Tự động hủy/thu hồi phòng khi quá 15 phút không check-in).

2. **Tổ chức họp theo Team & Dự án/Phòng ban (Group) động — P1 (`V6`, `V8`, `V9`, `V10`)**:
   - **Không dùng cứng Enum — Mọi Team và Dự án (Group) đều do Admin tạo động**:
     - **Team (`teams`, `user_teams`)**: Do Admin tạo tự do (`POST /api/v1/teams`, ví dụ: `Team Dev`, `Team BA`, `Team PM`). Khi Admin tạo hoặc cập nhật User (`POST /api/v1/users`, `PUT /api/v1/users/{id}`), Admin gán User vào các Team kèm vai trò/chức danh tự do (`memberRole`, ví dụ `"Backend Developer"`, `"Tech Lead"`).
     - **Group — Dự án / Phòng ban (`work_groups`, `user_groups`)**: Do Admin tạo tự do (`POST /api/v1/groups`, ví dụ: `Dự án A`, `Dự án B`, `Phòng Kỹ thuật`). Trong một `Team Dev`, có thể chỉ một vài thành viên tham gia `Dự án A`, các thành viên khác tham gia `Dự án B` (được thêm qua `POST /api/v1/groups/{id}/members`).
     - **Lựa chọn khi tạo cuộc họp (`POST /api/v1/bookings`)**: Người tạo cuộc họp có thể chọn tổ chức theo **Team (`teamId`)** hoặc theo **Dự án / Phòng ban (`groupId`)**, xem danh sách thành viên thuộc Team (`GET /api/v1/teams/{id}/members`) hoặc thuộc Dự án (`GET /api/v1/groups/{id}/members`), và **chọn đích danh những thành viên liên quan (`attendeeIds` / `optionalAttendeeIds`)** để họp (hoặc đặt `inviteAllScopeMembers = true` nếu muốn mời toàn bộ).
   - **Thành viên tham dự (`booking_attendees`) & Đồng bộ thông minh**:
     - Khi mời họp, hệ thống lưu snapshot danh sách thành viên vào `booking_attendees` tại thời điểm đặt (đảm bảo nhân sự vào/ra team hoặc dự án sau này không làm sai lệch lịch sử cuộc họp cũ).
     - Bản thân `organizer` cũng được lưu thành 1 dòng (`attendee_role = ORGANIZER`), giúp truy vấn **"Lịch họp của tôi"** (`GET /api/v1/bookings/me`) lấy được cả cuộc họp tôi tổ chức lẫn cuộc họp tôi được mời chỉ bằng **1 câu truy vấn duy nhất**.
     - **Cập nhật theo phần thay đổi (Diff-based Sync)**: Khi người tổ chức thêm/bớt thành viên trong `PUT /api/v1/bookings/{id}`, hệ thống giữ nguyên trạng thái `RSVP` (`ACCEPTED`/`DECLINED`), lý do từ chối và thời gian phản hồi của những thành viên cũ (chỉ đặt lại về `PENDING` nếu cuộc họp bị đổi khung giờ cần xác nhận lại), đồng thời gửi thông báo cho người mới được thêm và thông báo cập nhật giờ/phòng cho các thành viên hiện hữu.
     - **Phản hồi lời mời (`RSVP`)**: Thành viên có thể `ACCEPTED` hoặc `DECLINED` (kèm lý do). Khi `DECLINED`, khung giờ của họ được giải phóng cho cuộc họp khác; nếu sau đó muốn đổi lại từ `DECLINED` sang `ACCEPTED`, hệ thống tự động kiểm tra lại xung đột lịch cá nhân trước khi cho phép.
   - **Hệ thống thông báo (`notifications`)**: Tự động gửi thông báo khi được mời họp, khi cập nhật giờ/phòng, khi thành viên phản hồi RSVP, khi cuộc họp bị hủy, và khi cuộc họp sắp hết giờ (có `dedupe_key` chống gửi trùng và tự động kiểm soát độ dài tiêu đề an toàn).

3. **Tính năng nâng cao — P2 (`V7`)**:
   - **Cuộc họp kín (`is_confidential`)**: Khi đánh dấu họp kín, những người không thuộc cuộc họp (và không có quyền `BOOKING_MANAGE`) khi xem lịch biểu phòng hoặc chi tiết sẽ chỉ thấy tiêu đề hiển thị là `"Cuộc họp kín"`, đồng thời ẩn toàn bộ mô tả (`description`), danh sách người tham dự (`attendees`), người hủy (`cancelledBy`) và lý do hủy (`cancelReason`).
   - **Dấu vết thao tác nhạy cảm (`booking_audit_logs`)**: Khi Admin/Manager hủy lịch hoặc dời phòng của người khác (Admin Override), mọi thay đổi đều được ghi vết vào `booking_audit_logs`.
   - **Mở rộng Schema cho phỏng vấn nhiều lượt (`booking_slots`, `booking_guests`)**: Thiết kế sẵn ở tầng DB (với `DEFERRABLE INITIALLY IMMEDIATE` exclusion constraint) cho kịch bản HR đặt 1 phòng dài giờ và chia nhiều lượt phỏng vấn nối tiếp cho cả nhân sự nội bộ lẫn ứng viên bên ngoài.

---

## 4. Các quyết định kỹ thuật quan trọng & Lý do (Key Design Decisions & Trade-offs)

### 4.1. Chống đặt trùng lịch Phòng (Room Double Booking) ở 2 tầng: Application + PostgreSQL GiST Exclusion Constraint
- **Vấn đề**: Nếu chỉ kiểm tra trùng lịch bằng `SELECT COUNT(*)` ở tầng Java Service, khi 2 người bấm đặt cùng một phòng ở cùng một mili-giây (Race Condition dưới mức cô lập `READ COMMITTED`), cả 2 câu `SELECT` đều trả về `0` và cả 2 cùng `INSERT` thành công.
- **Giải pháp**:
  - Tầng Service kiểm tra nhanh để trả về thông báo lỗi thân thiện.
  - Tầng Database sử dụng tiện ích mở rộng `btree_gist` với ràng buộc loại trừ vật lý:
    ```sql
    CONSTRAINT excl_bookings_no_room_overlap
    EXCLUDE USING gist (
        room_id WITH =,
        tstzrange(start_time, end_time, '[)') WITH &&
    )
    WHERE (deleted_at IS NULL AND status IN ('CONFIRMED', 'IN_PROGRESS'))
    ```
  - **Lý do chọn khoảng nửa đóng `[)`**: Cho phép 2 cuộc họp nối tiếp nhau (ví dụ `09:00 - 10:00` và `10:00 - 11:00`) diễn ra hợp lệ mà không báo trùng ở mốc `10:00`.
  - Khi xảy ra xung đột đồng thời, PostgreSQL ném lỗi vi phạm `excl_bookings_no_room_overlap`, được `GlobalExceptionHandler` bắt chính xác và chuyển thành HTTP `409 Conflict` (`ROOM_TIME_CONFLICT`) theo chuẩn **RFC 7807 (`ProblemDetail`)**.

### 4.2. Chống đặt trùng lịch Cá nhân (Attendee Double Booking) & Phòng chống Deadlock khi có 2 Request đồng thời
- **Vấn đề**: Exclusion constraint trên `bookings` bảo vệ `room_id`, nhưng nếu 2 người tổ chức cùng lúc đặt **2 phòng khác nhau** trong cùng khung giờ và cùng mời chung một thành viên X (hoặc thành viên X bấm `ACCEPTED` lại một cuộc họp đã từ chối trước đó), kiểm tra `SELECT` đơn thuần sẽ bị Race Condition.
- **Giải pháp**:
  - Trước khi kiểm tra `checkAttendeeTimeConflicts` và ghi vào `booking_attendees` (trong cả `createBooking`, `updateBooking` và `respondRsvp`), hệ thống **sắp xếp danh sách UUID người tham gia theo thứ tự tăng dần** và thực hiện khóa bi quan mức dòng (`SELECT u FROM User u WHERE u.id IN :ids ORDER BY u.id ASC FOR UPDATE`).
  - **Lý do khóa theo thứ tự UUID tăng dần**: Đảm bảo mọi transaction đồng thời luôn lấy khóa theo đúng một chiều duy nhất, vừa tuần tự hóa việc kiểm tra/ghi lịch cá nhân của từng thành viên, vừa **triệt tiêu hoàn toàn nguy cơ Deadlock** khi 2 cuộc họp mời tập thành viên giao nhau.

### 4.3. Giải quyết bài toán "Giữ phòng ảo" (Ghost Meetings), Check-in an toàn & Trả phòng sớm
- **Check-in có kiểm soát xung đột thực tế & Auto `NO_SHOW`**:
  - Người tổ chức được phép check-in sớm tối đa 15 phút trước `start_time`.
  - Để tránh trường hợp cuộc họp A (`09:00 - 10:00`) vẫn đang diễn ra (`IN_PROGRESS`) mà cuộc họp B (`10:00 - 11:00`) bấm check-in sớm lúc `09:45` chiếm phòng, thao tác `checkIn` thực hiện khóa bi quan dòng `Room` (`SELECT ... FOR UPDATE`) và kiểm tra `existsInProgressBookingInRoom` để chặn nhận phòng khi cuộc họp trước chưa kết thúc.
  - `BookingScheduler` chạy định kỳ mỗi phút: nếu quá 15 phút sau `start_time` mà trạng thái vẫn là `CONFIRMED`, lịch đặt tự động chuyển sang `NO_SHOW`. Vì Exclusion Constraint chỉ khóa `status IN ('CONFIRMED', 'IN_PROGRESS')`, phòng lập tức được giải phóng cho người khác đặt.
- **Check-out giải phóng khung giờ**: Khi người tổ chức bấm `/check-out` sớm hơn `end_time` dự kiến, hệ thống cập nhật `end_time = actual_end_time` và chuyển sang `COMPLETED`, giải phóng phần thời gian thừa còn lại.

### 4.4. Bảo mật xác thực & Phân quyền chống Leo thang Đặc quyền (Privilege Escalation Protection)
- **Refresh Token Rotation, Reuse Detection & Đồng bộ hóa với Logout-All**:
  - Refresh Token là chuỗi ngẫu nhiên 256-bit (Opaque token), dưới DB chỉ lưu bản băm **SHA-256** (`token_hash`).
  - Khi xoay vòng token (`/api/v1/auth/refresh`) hoặc thu hồi toàn bộ token (`/api/v1/auth/logout-all`, đổi mật khẩu, khóa tài khoản), hệ thống đều lấy khóa bi quan trên dòng `User` (`findByIdForUpdate`), đảm bảo không bao giờ có refresh token mới sống sót sau lệnh `logout-all`.
  - Nếu một refresh token đã thu hồi bị gửi lên lần nữa (dấu hiệu lộ token), hệ thống lập tức thu hồi toàn bộ refresh token của user đó trên mọi thiết bị (`@Transactional(noRollbackFor = BusinessException.class)`).
- **Chống Leo thang Đặc quyền khi tạo/cập nhật User**:
  - Tách bạch quyền quản lý hồ sơ người dùng (`USER_CREATE`, `USER_UPDATE`) với quyền quản trị vai trò (`ROLE_MANAGE`).
  - Chỉ tài khoản có vai trò `ADMIN` mới được phép tạo mới, gán, sửa đổi hoặc xóa tài khoản `ADMIN` khác.
  - Người thao tác không bao giờ được phép gán một `Role` chứa bất kỳ quyền hạn (`permission`) nào vượt ngoài tập quyền mà chính người thao tác đang sở hữu.
- **Chống User Enumeration bằng Timing Attack**: Trong `AuthService.login`, khi email không tồn tại, hệ thống vẫn chạy `passwordEncoder.matches(rawPassword, dummyHash)` để thời gian phản hồi CPU luôn đồng nhất với trường hợp sai mật khẩu.

### 4.5. Toàn vẹn dữ liệu khi Xóa mềm (Soft Delete) & Tối ưu Hiệu năng Truy vấn
- **Bảo vệ Booking đang hoạt động trước khi xóa mềm User / Room**:
  - Các bảng chính (`users`, `rooms`, `bookings`) sử dụng cột `deleted_at` kết hợp `@SQLRestriction("deleted_at IS NULL")` và **Partial Unique Index** (`WHERE deleted_at IS NULL`).
  - Trước khi cho phép xóa mềm một `Room` hoặc xóa mềm / vô hiệu hóa một `User`, hệ thống khóa bản ghi và kiểm tra xem có lịch đặt phòng nào đang hoạt động (`CONFIRMED` hoặc `IN_PROGRESS` chưa kết thúc) gắn với phòng hoặc người tổ chức đó hay không. Nếu còn, hệ thống chặn xóa và yêu cầu hủy hoặc chuyển lịch trước, tránh việc bản ghi bị ẩn bởi `@SQLRestriction` trong khi vẫn giữ chỗ dưới DB.
- **Phân trang ở tầng SQL & Truy vấn theo lô (Batch Fetching)**:
  - Truy vấn phân trang `UserRepo.searchUsers` chỉ `JOIN FETCH` quan hệ `ManyToOne` (`u.role`), không `JOIN FETCH` collection `r.permissions` để PostgreSQL thực thi `LIMIT / OFFSET` trực tiếp thay vì phân trang trong bộ nhớ (in-memory pagination).
  - Các danh sách `Bookings`, `Users`, `Teams`, `Groups` đều tải dữ liệu liên quan theo lô (`WHERE ... IN :ids`) thay vì truy vấn lẻ từng dòng (loại bỏ bài toán N+1 query).

---

## 5. Danh mục nhóm API chính (Overview)

| Nhóm | Prefix | Các chức năng chính |
| :--- | :--- | :--- |
| **1. Authentication** | `/api/v1/auth` | Đăng nhập (`/login`), Làm mới token (`/refresh`), Đăng xuất 1 thiết bị (`/logout`), Đăng xuất mọi thiết bị (`/logout-all`), Hồ sơ hiện tại (`/me`). |
| **2. Users** | `/api/v1/users` | Xem/cập nhật hồ sơ cá nhân, đổi mật khẩu (`/me/password`), Admin CRUD & tìm kiếm người dùng (có bảo vệ chống leo thang đặc quyền và bảo vệ lịch họp đang hoạt động). |
| **3. Rooms** | `/api/v1/rooms` | Tìm kiếm & lọc phòng họp, **Tra cứu phòng trống theo khung giờ (`/available`)**, Quản trị phòng họp (`ROOM_CREATE`, `ROOM_UPDATE`, `ROOM_DELETE`). |
| **4. Bookings** | `/api/v1/bookings` | Đặt phòng theo Team (`teamId`) hoặc Dự án (`groupId`) và chọn các thành viên liên quan (`attendeeIds`), Lịch của tôi (`/me`), Lịch theo phòng (`/room/{roomId}/schedule`), Phản hồi **RSVP** (`/{id}/rsvp`), **Check-in**, **Check-out**, Hủy lịch (`/cancel`), Xem Audit Logs. |
| **5. Roles & Permissions** | `/api/v1/roles`, `/api/v1/permissions` | Xem danh sách quyền, Tạo/sửa/xóa vai trò động (Dynamic RBAC). |
| **6. Teams (Do Admin tạo động)** | `/api/v1/teams` | Admin tạo Team (VD: Team Dev, Team BA), gán thành viên kèm chức danh tự do và lấy danh sách thành viên (`GET /api/v1/teams/{id}/members`) để chọn người khi tạo lịch họp. |
| **7. Groups / Projects (Do Admin tạo động)** | `/api/v1/groups` | Admin tạo Dự án / Phòng ban (VD: Dự án A, Dự án B), chọn các thành viên liên quan từ các Team vào Dự án và lấy danh sách thành viên (`GET /api/v1/groups/{id}/members`) khi tạo lịch họp. |
| **8. Notifications** | `/api/v1/notifications` | Xem danh sách thông báo, đếm số thông báo chưa đọc (`/unread-count`), đánh dấu đã đọc. |
