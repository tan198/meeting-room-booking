# Meeting Room Booking System — Backend REST API

Hệ thống Backend REST API quản lý và đặt phòng họp nội bộ cho doanh nghiệp (~10 phòng họp, ~200 nhân viên), được thiết kế và nghiệm thu theo chuẩn **`meeting-room-spec.md`**: giải quyết triệt để tình trạng đặt trùng lịch phòng, phê duyệt độc lập chống tự duyệt (`SELF_APPROVAL_FORBIDDEN`), đổi lịch đã duyệt an toàn không mất phòng cũ (`BookingChange`), xử lý sự cố và chặn phòng nguyên tử (`RoomIssue` & `RoomBlock`), xác nhận sử dụng thực tế (`Check-in` / `Check-out` / `NO_SHOW`), phát thông báo theo phạm vi (`Announcement`), thống kê tách bạch sử dụng thực tế vs kết thúc suy định, và bảo vệ quyền riêng tư xuyên suốt cho các cuộc họp nhạy cảm (`CONFIDENTIAL`).

---

## 1. Công nghệ sử dụng (Tech Stack)

- **Ngôn ngữ & Framework**: Java 21, Spring Boot 3.5, Spring Security 6, Spring Data JPA (Hibernate 6).
- **Cơ sở dữ liệu & Migration**: PostgreSQL 17 (`btree_gist`, `JSONB`, Partial Unique Indexes, GiST Exclusion Constraints, Row-level Locking), Flyway (`V1` -> `V13`).
- **Bảo mật**: Stateless JWT (`HS256` với `nimbus-jose-jwt`) kết hợp Opaque Refresh Token Rotation (`SHA-256`), Dynamic RBAC (Role-Permission động), kiểm soát chống leo thang đặc quyền (Privilege Escalation Protection) và bảo vệ Admin hoạt động cuối cùng (`LAST_ADMIN_PROTECTED`).
- **Tài liệu API**: OpenAPI 3 / Swagger UI (`springdoc-openapi`).
- **Kiểm thử & Đóng gói**: JUnit 5, Testcontainers (PostgreSQL 17 thực tế), Docker & Docker Compose.

---

## 2. Cách chạy dự án (Quick Start)

### Cách 1: Chạy toàn bộ bằng Docker Compose (Khuyên dùng)

Dự án đã được cấu hình sẵn multi-stage `Dockerfile` và `docker-compose.yml` (tự động khởi tạo PostgreSQL 17, chạy toàn bộ Flyway migrations từ `V1` đến `V13` và khởi tạo **2 tài khoản Quản trị viên** để kiểm thử quy trình phê duyệt chéo `POL-01`).

```bash
# 1. Tạo file .env từ .env.example (nếu chưa có)
cp .env.example .env

# 2. Khởi chạy toàn bộ hệ thống (PostgreSQL + Backend API)
docker compose up --build -d
```

Sau khi container khởi động hoàn tất:
- **API Base URL**: `http://localhost:8000`
- **Swagger UI (Tài liệu & Test trực tiếp)**: `http://localhost:8000/swagger-ui.html`
- **Health Check & Readiness**: `http://localhost:8000/actuator/health`
- **Tài khoản Quản trị viên mặc định** (cấu hình qua biến môi trường trong `.env`):
  - **Admin 1**: `admin@meetingroom.local` / `Change_me_123!`
  - **Admin 2 (phục vụ duyệt chéo theo `POL-01` & `AC-06`)**: `admin2@meetingroom.local` / `Change_me_123!`

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

## 3. Kiến trúc nghiệp vụ & Bộ chính sách cốt lõi (`POL-01` .. `POL-23`)

### 3.1. Quy trình Gửi yêu cầu `PENDING` & Phê duyệt phân phòng `APPROVED` (`V11`)
- **Gửi yêu cầu (`POST /api/v1/bookings`)**:
  - Khởi tạo ở trạng thái `PENDING` (chưa giữ phòng chính thức — `POL-02`), cho phép nhiều yêu cầu `PENDING` cùng đề xuất một `preferredRoomId` mà không chặn nhau (`AC-02`), kèm cảnh báo `PREFERRED_ROOM_BUSY` hoặc `NO_MATCHING_ROOM` nếu phòng mong muốn đang bận.
  - Hỗ trợ phân loại độc lập (`POL-22`): `organizingTeamId` (`teamId`), `projectId` (`groupId`), `relatedDepartmentIds`, `meetingType` (`GENERAL`, `SYNC`, `PLANNING`, `REVIEW`, `SALARY_REVIEW`, `PERFORMANCE_REVIEW`, `INTERVIEW`, `TRAINING`, `OTHER`) và mức riêng tư `visibility` (`STANDARD`, `CONFIDENTIAL`).
  - Các loại nhạy cảm (`SALARY_REVIEW`, `PERFORMANCE_REVIEW`, `INTERVIEW`) bắt buộc `visibility = CONFIDENTIAL` và không bao giờ được hạ về `STANDARD` (`POL-23`, `AC-72`).
- **Phê duyệt / Từ chối (`POST /api/v1/bookings/{id}/approve` & `/reject`)**:
  - Yêu cầu một Quản trị viên **khác người tổ chức** xử lý (`SELF_APPROVAL_FORBIDDEN` — `POL-01`, `AC-06`).
  - Kiểm tra `expectedVersion` (`VERSION_CONFLICT` — `AC-07`), kiểm tra lại tư cách Team/Dự án của người tổ chức và trạng thái `ACTIVE` của người tham dự (`REFERENCE_NO_LONGER_VALID` — `AC-11`, `AC-75`), kiểm tra sức chứa, trạng thái phòng và khoảng chặn `RoomBlock` (`AC-09`, `AC-24`), gán phòng chính thức và chuyển sang `APPROVED` + `NOT_CHECKED_IN`.
- **Tự động hết hạn (`EXPIRED`)**: Nếu tới giờ bắt đầu (`startTime <= now`) mà yêu cầu `PENDING` chưa được duyệt, hệ thống tự động chuyển sang `EXPIRED` (`POL-12`, `AC-08`).

### 3.2. Đề nghị thay đổi lịch đã duyệt an toàn — `BookingChange` (`V12`, `POL-10`, `AC-14`..`AC-17`)
- Khi lịch đã `APPROVED`, chủ lịch gửi đề nghị thay đổi qua `POST /api/v1/bookings/{id}/changes` thay vì sửa trực tiếp:
  - Lịch gốc vẫn giữ nguyên trạng thái `APPROVED` và **vẫn giữ phòng cũ** trong suốt thời gian chờ Admin duyệt (`AC-14`).
  - Mỗi booking có tối đa 1 đề nghị `PENDING` tại một thời điểm (bảo vệ bởi partial unique index `uq_booking_changes_one_pending`).
  - Khi Admin duyệt (`POST /api/v1/booking-changes/{id}/approve`), hệ thống kiểm tra phòng mới (bỏ qua chính lịch gốc), cập nhật lịch gốc và đóng đề nghị sang `APPROVED` trong một giao dịch nguyên tử (`AC-16`). Nếu bị từ chối (`/reject`) hoặc rút lại (`/withdraw`), lịch gốc giữ nguyên không đổi (`AC-15`, `AC-17`).

### 3.3. Báo cáo sự cố (`RoomIssue`) & Chặn phòng nguyên tử (`RoomBlock`) (`V12`, `POL-14`, `POL-15`, `AC-18`..`AC-26`)
- **Báo cáo sự cố (`POST /api/v1/room-issues`)**:
  - Mọi nhân viên `ACTIVE` được báo sự cố (`EQUIPMENT`, `FACILITY`, `CLEANLINESS`, `SAFETY`, `OTHER`) với mức ảnh hưởng đề xuất (`DEGRADED`, `UNUSABLE`).
  - Tạo báo cáo sự cố **không tự động** chặn phòng hay hủy lịch (`POL-14`, `AC-20`); đóng sự cố (`RESOLVED` / `CLOSED`) cũng **không tự động** gỡ `RoomBlock` (`AC-25`).
- **Chặn phòng kèm Kế hoạch xử lý nguyên tử (`POST /api/v1/room-blocks/preview` & `POST /api/v1/room-blocks`)**:
  - Bước 1 (`/preview`): Liệt kê toàn bộ lịch `APPROVED` bị ảnh hưởng trong khoảng chặn kèm `version` hiện tại và danh sách phòng trống thay thế gợi ý (`suggestedRooms`).
  - Bước 2 (`POST /api/v1/room-blocks`): Bắt buộc truyền đầy đủ phương án `resolutions` (`REASSIGN` hoặc `CANCEL`) cho từng booking bị ảnh hưởng. Nếu thiếu phương án trả `409 IMPACT_PLAN_INCOMPLETE` (`AC-21`); nếu phát sinh lịch mới hoặc lệch `expectedVersion` trả `409 IMPACT_PLAN_STALE` (`AC-22`). Toàn bộ việc chuyển phòng/hủy lịch và lưu `RoomBlock` thực thi **nguyên tử trong một giao dịch DB** (`AC-23`).

### 3.4. Xác nhận sử dụng thực tế (`Check-in` / `Check-out` / `NO_SHOW`) (`POL-18`..`POL-21`, `AC-47`..`AC-63`)
- **Cửa sổ Check-in nghiêm ngặt**: `startAt <= serverNow < min(startAt + 10 phút, endAt)`. Không cho phép check-in trước giờ bắt đầu (`CHECK_IN_TOO_EARLY` — `AC-47`) và không cho phép check-in sau hạn (`CHECK_IN_DEADLINE_PASSED` — `AC-51`).
- **Tự động hủy `NO_SHOW`**: Quá `checkInDeadline` mà chưa check-in, lịch tự chuyển sang `CANCELLED` (`cancellationReasonCode = NO_SHOW`) + `usageStatus = NO_SHOW` và giải phóng phòng từ mốc `checkInDeadline` (`AC-50`).
- **Check-out giải phóng sớm (`releasedAt`)**: Khi check-out trước `endTime`, hệ thống ghi nhận `releasedAt = actualEndTime` để giải phóng ngay phần thời gian còn lại cho cuộc họp khác mà **vẫn giữ nguyên `endTime` dự kiến** phục vụ đối chiếu lịch sử và thống kê (`AC-55`).
- **Tự kết thúc theo giờ (`SYSTEM_SCHEDULED_END`)**: Nếu đã check-in (`IN_USE`) nhưng tới `endTime` chưa bấm check-out, hệ thống tự đóng phiên với `endSource = SYSTEM_SCHEDULED_END` (`checkedOutBy = null`) để tách bạch với thời gian có xác nhận kết thúc (`AC-57`).

### 3.5. Thông báo chung (`Announcement`), Báo cáo (`Reports`) & Chống gửi lặp (`Idempotency-Key`) (`V13`)
- **Thông báo chung (`/api/v1/announcements`)**: Admin xem trước số người nhận `ACTIVE` duy nhất (`/preview`) và phát hành theo phạm vi (`ALL`, `DEPARTMENTS`, `TEAMS`, `PROJECTS`, `BOOKINGS`), chốt danh sách người nhận tại thời điểm phát hành (`AC-28`, `AC-41`).
- **Thống kê (`/api/v1/reports/bookings` & `/api/v1/reports/usage`)**: Tính phần giao thời lượng trong khoảng báo cáo (tối đa 366 ngày — `AC-43`), tách bạch giờ bố trí, giờ giữ phòng hiệu lực, giờ sử dụng xác nhận, giờ kết thúc suy định, giờ giải phóng sớm và tỷ lệ `NO_SHOW` (`AC-64`..`AC-66`), đồng thời gộp các cuộc họp `CONFIDENTIAL` thành nhóm riêng để bảo mật thông tin HR (`AC-76`).
- **Hỗ trợ `Idempotency-Key` (`IdempotencyFilter`)**: Các thao tác `POST` gửi kèm header `Idempotency-Key` trong 24 giờ sẽ trả lại kết quả cũ nếu cùng nội dung (`AC-30`) hoặc trả `409 IDEMPOTENCY_KEY_REUSED` nếu khác nội dung.

---

## 4. Danh mục nhóm API chính (API Overview)

| Nhóm | Prefix / Endpoint tiêu biểu | Các chức năng chính |
| :--- | :--- | :--- |
| **1. Authentication** | `/api/v1/auth`, `/api/v1/me` | Đăng nhập (`/login`), Làm mới token (`/refresh`), Đăng xuất (`/logout`, `/logout-all`), Danh tính hiện tại (`/api/v1/me`). |
| **2. Users & Roles** | `/api/v1/users`, `/api/v1/roles` | Quản lý tài khoản, đổi mật khẩu (`/me/password`, `/me/change-password`), Admin reset mật khẩu (`/users/{id}/reset-password`), bảo vệ Admin cuối cùng (`LAST_ADMIN_PROTECTED`). |
| **3. Rooms & Equipments** | `/api/v1/rooms`, `/api/v1/equipment` | Quản lý phòng họp, danh mục thiết bị (`/api/v1/equipment`), cấu hình thiết bị phòng (`PUT /rooms/{id}/equipment`), tìm phòng trống (`/rooms/availability`, `/rooms/available`). |
| **4. Bookings & Changes** | `/api/v1/bookings`, `/api/v1/booking-changes`, `/api/v1/meeting-types` | Gửi yêu cầu `PENDING`, Admin `/approve`, `/reject`, `/reassign-room`, Đề nghị đổi lịch `/bookings/{id}/changes` (`/approve`, `/reject`, `/withdraw`), `/check-in`, `/check-out`, `/cancel`, Lịch sử `/bookings/{id}/history`. |
| **5. Teams & Projects/Depts** | `/api/v1/teams`, `/api/v1/me/teams`, `/api/v1/groups`, `/api/v1/departments`, `/api/v1/projects` | Quản lý Team, Phòng ban và Dự án động cùng danh sách thành viên. |
| **6. Room Issues** | `/api/v1/room-issues` | Báo cáo sự cố phòng/thiết bị, chuyển trạng thái xử lý (`/{id}/transitions`), lịch sử xử lý (`/{id}/history`). |
| **7. Room Blocks** | `/api/v1/room-blocks` | Xem trước tác động (`/preview`), tạo khoảng chặn nguyên tử kèm kế hoạch chuyển/hủy lịch (`POST /room-blocks`), hủy chặn (`/{id}/cancel`), thay thế chặn (`/{id}/replace`). |
| **8. Notifications & Announcements** | `/api/v1/me/notifications`, `/api/v1/announcements` | Hộp thông báo cá nhân (`/unread-count`, `/{id}/read`, `/read-all`) và phát hành thông báo chung theo phạm vi (`/announcements/preview`, `POST /announcements`). |
| **9. Reports & Analytics** | `/api/v1/reports/bookings`, `/api/v1/reports/usage` | Báo cáo tổng hợp đặt phòng và báo cáo sử dụng phòng thực tế vs suy định, tỷ lệ `NO_SHOW`. |
