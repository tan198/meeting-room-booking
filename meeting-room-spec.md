# ĐẶC TẢ NGHIỆP VỤ HỆ THỐNG QUẢN LÝ ĐẶT PHÒNG HỌP

**Loại tài liệu:** Đặc tả yêu cầu nghiệp vụ và chức năng hoàn chỉnh — 30/10/2026  
**Quy mô mục tiêu:** khoảng 200 nhân viên, 10 phòng họp, một công ty.  
**Mục đích:** Cơ sở thống nhất để thiết kế dữ liệu, API, triển khai và nghiệm thu trong phạm vi hệ thống đã xác định. Các quy tắc do phân tích lựa chọn được phân biệt với yêu cầu người dùng; tài liệu không tuyên bố những quy tắc này đã được người dùng phê duyệt riêng.  
**Nền tảng theo đề bài:** Java 17 trở lên, Spring Boot 3, PostgreSQL, REST API, Docker Compose; repository có lịch sử commit và README giải thích cách chạy, thiết kế, quyết định quan trọng.

## 1. Mục tiêu và nguồn yêu cầu

Hệ thống thay thế việc đặt phòng qua tin nhắn, cung cấp lịch thống nhất và ngăn một phòng được xác nhận cho hai cuộc họp trùng thời gian.

| Nguồn | Nội dung |
|---|---|
| Đề bài gốc | Quản lý phòng, đặt phòng, xem lịch, tìm phòng trống; có nhân viên và quản trị viên |
| Phạm vi người dùng bổ sung | Phòng ban/dự án, admin sắp xếp và phê duyệt; thông báo; check-in/check-out, xác nhận sử dụng thực tế và tự hủy do không đến |
| Quy tắc thiết kế nghiệp vụ của tài liệu | Mọi yêu cầu đều cần admin duyệt; yêu cầu chờ duyệt không giữ phòng; không tự duyệt; thay đổi lịch đã duyệt dùng đề nghị riêng; thông báo trong ứng dụng |

Mỗi yêu cầu cần trả lời được: ai tổ chức, thuộc đơn vị/dự án nào, cần gì, khi nào, ai xử lý, được bố trí phòng nào và những ai phải được thông báo.

## 2. Phạm vi hệ thống

### 2.1. Trong phạm vi

- Đăng nhập, tài khoản nhân viên, vai trò hệ thống; admin tạo và khóa tài khoản.
- Quản lý phòng ban, team độc lập và dự án; quản lý thành viên team/dự án.
- Đặt phòng cho team, dự án, một/nhiều phòng ban hoặc nhu cầu khác; phân loại mục đích họp và mức riêng tư.
- Quản lý phòng, sức chứa và thiết bị.
- Tìm phòng trống, xem lịch phòng và lịch cá nhân.
- Gửi yêu cầu, phê duyệt, từ chối, hủy; admin phân phòng phù hợp.
- Đề nghị thay đổi lịch đã duyệt và duyệt lại mà không làm mất lịch cũ khi thay đổi chưa được chấp thuận.
- Danh sách người tham dự nội bộ; số người tổng có thể bao gồm khách ngoài công ty.
- Báo cáo sự cố, theo dõi xử lý, chặn sử dụng phòng theo khoảng thời gian.
- Thông báo nghiệp vụ tự động và thông báo chung do admin đăng.
- Check-in/check-out, xác nhận sử dụng theo tài khoản, kết thúc khi hết giờ và tự hủy do không đến.
- Lịch sử xử lý; thống kê lịch bố trí, số lần không đến và thời gian sử dụng có xác nhận/theo suy định.

### 2.2. Ranh giới hệ thống

Các chức năng sau không thuộc sản phẩm được đặc tả. Đây là ranh giới trách nhiệm, không phải danh sách công việc còn thiếu hoặc hẹn triển khai sau:

- Đặt lặp, duyệt nhiều cấp, ủy quyền duyệt, đặt hộ.
- Vai trò trưởng phòng/PM có quyền duyệt riêng; hạn mức hoặc thứ hạng ưu tiên theo chức danh.
- SSO, đồng bộ nhân sự, Google/Outlook Calendar, email/SMS/push.
- Đăng ký tài khoản công khai; tài khoản cho khách ngoài; RSVP của người tham dự.
- Tệp đính kèm sự cố, chat, thiết bị IoT, phân bổ nhiều phòng cho một yêu cầu.
- Nhiều công ty, nhiều múi giờ nghiệp vụ.

## 3. Thuật ngữ

| Thuật ngữ | Định nghĩa |
|---|---|
| Yêu cầu đặt phòng / Booking | Hồ sơ từ khi nhân viên gửi đến khi được duyệt, từ chối, hết hạn hoặc hủy |
| Lịch chính thức | Booking đã được duyệt; phòng và khoảng thời gian đã xác nhận |
| Người tổ chức | Người tạo yêu cầu và chịu trách nhiệm quản lý hồ sơ |
| Phòng ban tổ chức | Phòng ban của người tổ chức tại thời điểm gửi yêu cầu |
| Dự án liên quan | Dự án được gắn vào cuộc họp; không bắt buộc |
| Team | Nhóm làm việc độc lập với dự án và phòng ban; thành viên có thể thuộc nhiều đơn vị |
| Phòng ban liên quan | Các đơn vị phối hợp trong cuộc họp; không đồng nghĩa đơn vị chủ trì hoặc danh sách người có quyền xem |
| Loại cuộc họp | Mục đích: họp định kỳ nội bộ, lập kế hoạch, review, phỏng vấn, review lương... |
| Mức riêng tư | STANDARD hoặc CONFIDENTIAL; quyết định trường dữ liệu từng người được xem |
| Người tham dự | Nhân viên được người tổ chức đưa vào danh sách mời |
| RoomBlock | Khoảng thời gian phòng bị chặn vì bảo trì, sự cố hoặc vận hành |
| Đề nghị thay đổi / BookingChange | Phiên bản thông tin mới chờ duyệt của một lịch chính thức |
| Khoảng thời gian | Dạng [start, end): bao gồm bắt đầu, không bao gồm kết thúc |
| BookingUsage | Trạng thái và bằng chứng xác nhận sử dụng của cuộc họp, tách khỏi quyết định phê duyệt |
| RoomAllocation | Khoảng chiếm phòng thực sự được bảo vệ chống trùng, có thể được giải phóng sớm |
| UsageSegment | Đoạn sử dụng ghi nhận ở một phòng, từ lúc check-in/chuyển đến tới lúc kết thúc/chuyển đi |

## 4. Tác nhân và phân quyền

### 4.1. Vai trò

- `USER`: nhân viên; thao tác với hồ sơ của mình, xem phòng/lịch và báo cáo sự cố.
- `ADMIN`: có các quyền của nhân viên và quyền quản lý, phân phòng, phê duyệt, vận hành.
- Vai trò dự án `MANAGER`/`MEMBER` và vai trò team `LEAD`/`MEMBER` mô tả trách nhiệm trong từng nhóm; không cấp quyền quản trị đặt phòng.
- Vai trò phòng ban gồm HEAD và MEMBER, lưu theo quan hệ người–phòng ban. HEAD phục vụ nhận diện trách nhiệm tổ chức, không được duyệt hoặc hủy lịch của cấp dưới nếu không có quyền ADMIN. Mỗi phòng ban ACTIVE có tối đa một HEAD; có thể tạm thời chưa bổ nhiệm HEAD.

### 4.2. Ma trận quyền

| Hành động | USER | ADMIN |
|---|---|---|
| Xem danh mục phòng hoạt động, tìm phòng trống | Có | Có |
| Xem lịch bận/trống toàn công ty | Có, thông tin giới hạn | Có |
| Tạo yêu cầu | Cho bản thân | Cho bản thân |
| Xem đầy đủ một booking | Chủ lịch hoặc người tham dự | STANDARD: có; CONFIDENTIAL: chỉ nếu là chủ lịch/người tham dự |
| Sửa yêu cầu PENDING | Chủ yêu cầu | Không sửa thay; yêu cầu người tạo cập nhật |
| Đề nghị thay đổi lịch APPROVED | Chủ lịch, trước giờ bắt đầu | Như USER nếu là chủ lịch |
| Duyệt/từ chối/phân phòng | Không | Có, trừ yêu cầu do chính mình tạo |
| Hủy PENDING | Chủ yêu cầu | Có, kèm lý do |
| Hủy APPROVED | Chủ lịch trước giờ bắt đầu | Có trước khi kết thúc, kèm lý do |
| Chuyển phòng do sự cố | Không | Có, giữ nguyên thời gian và đáp ứng nhu cầu |
| Quản lý phòng, phòng ban, team, dự án, tài khoản | Không | Có |
| Tạo báo cáo sự cố | Có | Có |
| Xử lý sự cố, tạo/hủy RoomBlock | Không | Có |
| Check-in | Chủ lịch hoặc người tham dự ACTIVE | Như USER; có thể xác nhận vận hành kèm lý do |
| Check-out | Chủ lịch hoặc người đã check-in | Có, kèm lý do nếu can thiệp |
| Đăng thông báo chung | Không | Có |
| Đọc/đánh dấu thông báo | Chỉ thông báo của mình | Chỉ thông báo của mình |

Admin không được tự duyệt cả yêu cầu gốc lẫn đề nghị thay đổi của mình. Quy tắc này yêu cầu có ít nhất hai tài khoản admin hoạt động nếu admin cũng cần đặt phòng; không được vô hiệu hóa admin hoạt động cuối cùng. Nhân viên không được cấp quyền dựa trên dữ liệu role/userId do client gửi lên.

## 5. Mô hình tổ chức

### 5.1. Nhân viên và phòng ban

- Mỗi nhân viên hoạt động thuộc đúng một phòng ban chính và mang vai trò HEAD hoặc MEMBER trong phòng ban đó. Chuyển phòng ban và vai trò phải được admin cập nhật cùng một thao tác; ghi lịch sử hiệu lực.
- Mã nhân viên và email đăng nhập là duy nhất.
- Tài khoản `ACTIVE` được đăng nhập, gửi yêu cầu, tham gia dự án; `INACTIVE` không được sử dụng hệ thống, kể cả bằng phiên đăng nhập cũ.
- Người tổ chức luôn được backend xác định từ tài khoản đăng nhập.
- Phòng ban tổ chức được backend ghi nhận lúc gửi; client không chọn tùy ý phòng ban khác.
- Khi nhân viên chuyển phòng ban, hồ sơ cũ giữ phòng ban tổ chức ban đầu để không làm thay đổi thống kê lịch sử.
- Khóa tài khoản không tự hủy lịch. Thao tác phải trả kèm số lịch tương lai của người đó để admin xử lý tiếp; đây không phải đặt hộ hoặc chuyển chủ lịch.

### 5.2. Dự án

- Một người tham gia nhiều dự án; một dự án có nhiều thành viên.
- Mỗi cặp người–dự án có một tư cách thành viên hiện hành, vai trò `MANAGER` hoặc `MEMBER`, và trạng thái hoạt động.
- Chỉ được gắn dự án đang `ACTIVE` mà người tổ chức đang là thành viên hoạt động.
- Booking không bắt buộc có dự án. Người tham dự không nhất thiết cùng dự án/phòng ban với người tổ chức.
- Dự án `ARCHIVED` không nhận yêu cầu mới; lịch và dữ liệu cũ được giữ nguyên.
- Khi thành viên rời dự án, booking đã duyệt không tự mất hiệu lực. Yêu cầu chờ duyệt hoặc đề nghị thay đổi cần được kiểm tra lại tư cách tại lúc duyệt.
- Danh mục đã được tham chiếu chỉ được ngừng sử dụng/lưu trữ; không xóa cứng.

## 6. Quản lý phòng và thiết bị

### 6.1. Thông tin phòng

| Trường | Quy tắc |
|---|---|
| code | Bắt buộc, duy nhất |
| name | Bắt buộc |
| location | Bắt buộc, vị trí/tầng/khu vực |
| capacity | Số nguyên lớn hơn 0 |
| status | ACTIVE hoặc INACTIVE |
| description | Không bắt buộc |
| equipment | Danh sách loại thiết bị và số lượng sử dụng được |

- Thiết bị có mã duy nhất, tên; mỗi cặp phòng–thiết bị xuất hiện một lần với số lượng nguyên không âm.
- Toàn bộ sức chứa của phòng dành cho một cuộc họp; không dùng chung phòng cùng lúc.
- Chỉ phòng ACTIVE mới có thể được đề xuất, phân hoặc duyệt.
- INACTIVE áp dụng ngừng sử dụng không xác định thời hạn; bảo trì có thời gian cụ thể dùng RoomBlock.
- Chặn thao tác chuyển INACTIVE, giảm sức chứa hoặc giảm thiết bị nếu làm bất kỳ lịch còn phần RoomAllocation giữ hiệu lực từ hiện tại trở đi nào không còn hợp lệ. Trả danh sách lịch ảnh hưởng để admin chuyển phòng/hủy trước.
- Phòng đã có dữ liệu tham chiếu không xóa cứng; sửa tên/vị trí không thay đổi lịch sử quyết định xử lý.

## 7. Tìm phòng trống và xem lịch

### 7.1. Đầu vào và kết quả

Đầu vào: startAt, endAt, tổng số người; thiết bị/số lượng yêu cầu và vị trí là bộ lọc tùy chọn.

Một phòng phù hợp khi đồng thời:

1. ACTIVE và đủ sức chứa.
2. Có đủ số lượng từng thiết bị bắt buộc.
3. Không giao với khoảng chiếm phòng hiệu lực trong RoomAllocation của phòng đó (xem phần 25).
4. Không giao với RoomBlock còn hiệu lực.

Kết quả gồm id, mã/tên phòng, vị trí, sức chứa, thiết bị. Sắp xếp mặc định theo sức chứa tăng dần rồi mã phòng để gợi ý sử dụng tiết kiệm không gian. Đây là gợi ý; admin chọn phòng cuối cùng.

### 7.2. Quy tắc thời gian và giao nhau

- Múi giờ nghiệp vụ: Asia/Ho_Chi_Minh. API nhận/trả thời gian ISO 8601 có offset; lưu các mốc như thời điểm tuyệt đối.
- Độ chính xác nghiệp vụ đến phút; từ chối giây/phần giây khác 0 thay vì âm thầm làm tròn.
- Quy tắc đến phút áp dụng giờ họp do người dùng nhập. Thời điểm audit/thông báo và startAt của RoomBlock bắt đầu ngay giữ độ chính xác của đồng hồ backend, không làm tròn gây chặn lùi thời gian.
- startAt < endAt; bắt đầu phải ở tương lai khi gửi, duyệt, sửa hoặc duyệt thay đổi.
- Quy tắc: khoảng họp nằm trong một ngày địa phương; cho phép kết thúc đúng 00:00 ngày kế tiếp nếu đó là ranh giới cuối ngày, không cho kéo dài qua ngày kế tiếp.
- Cho phép đặt mọi ngày và mọi giờ, không giới hạn số ngày đặt trước; thời gian đệm bằng 0. Thời lượng tối thiểu một phút; thời lượng tối đa tới ranh giới cuối ngày.
- Hai khoảng giao nhau nếu `newStart < existingEnd AND newEnd > existingStart`.
- Với lịch 09:00–10:00, lịch 10:00–11:00 hợp lệ; lịch 09:30–10:30 không hợp lệ.
- PENDING, REJECTED, CANCELLED, EXPIRED không giữ phần thời gian tương lai. Booking APPROVED giữ phòng theo RoomAllocation hiệu lực; check-out sớm hoặc tự hủy NO_SHOW giải phóng phần còn lại. RoomBlock ACTIVE vẫn chặn khoảng của nó.
- Kết quả tìm trống không giữ chỗ. Khi duyệt phải kiểm tra lại trên dữ liệu hiện tại.
- Không lưu trường isAvailable cố định trên Room.

### 7.3. Lịch và quyền riêng tư

- Lịch phòng cho phép lọc theo phòng và khoảng ngày; lịch cá nhân gồm lịch do mình tổ chức hoặc tham dự.
- Khi lấy lịch trong khoảng, lấy tất cả lịch giao với khoảng đó, không chỉ lịch có giờ bắt đầu nằm trong khoảng.
- Với STANDARD, nhân viên không liên quan chỉ thấy phòng, giờ bận, mã booking và tên người tổ chức; không thấy tiêu đề, mục đích, danh sách người dự, dự án hay lý do xử lý. Với CONFIDENTIAL chỉ thấy phòng/giờ bận/nhãn riêng tư, không có tên hoặc liên kết định danh chi tiết theo phần 27.
- Chủ lịch/người tham dự được xem chi tiết; ADMIN xem đầy đủ STANDARD, nhưng với CONFIDENTIAL chỉ xem dữ liệu cần bố trí/vận hành nếu không phải chủ lịch/người dự. Quy tắc che trường ở phần 27. Lịch bận/trống dùng RoomAllocation hiệu lực và RoomBlock; lịch sử hiển thị theo cùng quyền truy cập.
- Danh sách riêng của người tổ chức có đầy đủ trạng thái. Admin có danh sách yêu cầu để lọc phòng ban/dự án/trạng thái/người tổ chức/thời gian họp và sắp xếp theo thời điểm gửi.

## 8. Yêu cầu đặt phòng

### 8.1. Dữ liệu gửi yêu cầu

| Trường | Bắt buộc | Quy tắc |
|---|---|---|
| title | Có | 1–200 ký tự sau khi loại khoảng trắng đầu/cuối |
| purpose | Có | 1–2.000 ký tự |
| startAt, endAt | Có | Theo mục 7 |
| attendeeCount | Có | Số nguyên >= 1, tính cả người tổ chức và khách ngoài |
| participantIds | Không | Nhân viên ACTIVE, không trùng; backend luôn đưa người tổ chức vào danh sách |
| meetingType | Có | Mục đích độc lập với team/dự án/phòng ban, theo phần 27 |
| visibility | Không | STANDARD mặc định; loại nhạy cảm bắt buộc CONFIDENTIAL |
| organizingTeamId | Không | Team ACTIVE, người tổ chức là thành viên ACTIVE |
| relatedDepartmentIds | Không | Các đơn vị ACTIVE phối hợp; không cấp quyền xem |
| projectId | Không | Kiểm tra tư cách dự án |
| preferredRoomId | Không | Phòng mong muốn, phải ACTIVE nếu chọn |
| requiredEquipment | Không | Loại thiết bị hợp lệ, số lượng nguyên > 0, không trùng loại |
| note | Không | Tối đa 2.000 ký tự |

- Số người tổng phải >= số người nội bộ trong danh sách đã bao gồm người tổ chức.
- Khách ngoài chỉ được phản ánh trong tổng số người; không lưu thông tin định danh khách.
- Không yêu cầu chọn phòng mong muốn. Nếu có chọn, phòng phải đáp ứng sức chứa/thiết bị tại lúc gửi; được phép gửi khi phòng mong muốn đang bận vì admin có thể bố trí phòng khác. Phản hồi cần nêu rõ phòng mong muốn chưa được giữ.
- Nếu không có phòng nào đáp ứng nhu cầu hiện tại, vẫn nhận yêu cầu PENDING và trả cảnh báo; admin quyết định từ chối hoặc trao đổi điều chỉnh.
- Không chặn trùng lịch người tham dự. Cuộc họp vẫn có thể hợp lệ dù một người được mời ở hai lịch cùng lúc.

### 8.2. Gửi và sửa PENDING

1. Xác thực tài khoản, dữ liệu, phòng ban/dự án và danh sách tham dự.
2. Tạo booking PENDING; chưa có phòng chính thức.
3. Lưu dấu thời gian, người tạo và bản ghi lịch sử.
4. Tạo sự kiện thông báo cho admin hoạt động, trừ chính người gửi nếu người gửi là admin.

Chủ yêu cầu được sửa PENDING trước giờ bắt đầu. Lưu lịch sử thay đổi, tăng phiên bản hồ sơ, thông báo cho admin; giữ nguyên thời điểm gửi ban đầu để xếp hàng. Admin phải duyệt đúng phiên bản mình đã xem.

### 8.3. Phê duyệt

Admin chọn phòng và có thể nhập ghi chú. Hệ thống kiểm tra trong cùng thao tác xác nhận:

- Booking còn PENDING, đúng phiên bản, chưa đến giờ bắt đầu.
- Người tổ chức và người dự còn ACTIVE; team/dự án cùng tư cách thành viên còn hợp lệ nếu có liên kết; các phòng ban liên quan còn hợp lệ. Không cấp quyền xem nội dung CONFIDENTIAL chỉ vì cần duyệt.
- Người duyệt là admin hoạt động và không phải người tổ chức.
- Phòng ACTIVE, đủ sức chứa/thiết bị, không giao lịch đã duyệt hoặc RoomBlock.

Nếu hợp lệ: gán phòng chính thức, chuyển APPROVED, lưu người/giờ duyệt và thông báo người tổ chức cùng người tham dự. Nếu thất bại: không đổi trạng thái, không chiếm phòng, trả lý do cụ thể.

Admin được chọn phòng khác phòng mong muốn nếu vẫn đáp ứng mọi nhu cầu. Admin không tự đổi giờ hoặc số người khi duyệt; người tổ chức cần sửa PENDING trước.

Admin ưu tiên xử lý yêu cầu gửi trước khi các nhu cầu tương đương. Hệ thống không có quyền ưu tiên phòng ban/chức danh và không tự lấy phòng từ một lịch đã được duyệt.

### 8.4. Từ chối

- Chỉ admin không phải người tổ chức được từ chối booking PENDING còn thời gian.
- Lý do bắt buộc, 1–2.000 ký tự.
- Chuyển REJECTED, lưu lịch sử, thông báo người tổ chức.
- REJECTED là kết thúc; muốn gửi lại phải tạo yêu cầu mới.

### 8.5. Hủy và hết hạn

- Chủ yêu cầu được hủy PENDING hoặc APPROVED trước startAt; phải nhập lý do.
- Admin được hủy PENDING hoặc APPROVED trước endAt vì vận hành; phải nhập lý do. Lịch đang diễn ra chỉ admin được hủy.
- Hủy APPROVED giải phóng phần thời gian còn lại và kết thúc mọi đề nghị thay đổi đang chờ với lý do lịch gốc bị hủy. Nếu đang sử dụng, đóng UsageSegment tại thời điểm hủy và ghi kết quả INTERRUPTED; không xóa thời gian đã sử dụng. Không hủy/sửa hồ sơ đã COMPLETED hoặc INTERRUPTED.
- Hủy PENDING: thông báo chủ yêu cầu nếu do admin hủy và admin được phép xử lý; chưa thông báo người tham dự vì chưa có lịch chính thức.
- Hủy APPROVED: thông báo chủ lịch và người tham dự.
- Khi startAt <= hiện tại, PENDING không còn được sửa/duyệt/từ chối; chuyển EXPIRED và thông báo chủ yêu cầu một lần. Dù tác vụ cập nhật trạng thái chưa chạy, mọi thao tác vẫn phải kiểm tra giờ hiện tại.
- Hủy lại một booking đã CANCELLED trả trạng thái hiện có, không tạo lại sự kiện hủy. REJECTED/EXPIRED không chuyển sang CANCELLED.

### 8.6. Trạng thái booking

| Trạng thái | Chuyển sang | Điều kiện |
|---|---|---|
| PENDING | APPROVED | Admin duyệt hợp lệ |
| PENDING | REJECTED | Admin từ chối, có lý do |
| PENDING | CANCELLED | Chủ yêu cầu/admin hủy hợp lệ |
| PENDING | EXPIRED | Đã tới giờ bắt đầu mà chưa được duyệt |
| APPROVED | CANCELLED | Hủy hợp lệ hoặc SYSTEM tự hủy NO_SHOW theo phần 25 |
| REJECTED/CANCELLED/EXPIRED | Không chuyển tiếp | Tạo yêu cầu mới nếu cần |

Booking.status phản ánh quyết định xử lý; BookingUsage.status phản ánh sử dụng. APPROVED + NOT_CHECKED_IN được hiển thị Sắp diễn ra hoặc Chờ check-in theo giờ; IN_USE mới hiển thị Đang sử dụng; COMPLETED là đã kết thúc phiên sử dụng. Không hiển thị Đang sử dụng chỉ vì đã tới giờ. Tự hủy do không đến chuyển booking sang CANCELLED với cancellationReasonCode=NO_SHOW và usage sang NO_SHOW. Quy tắc đầy đủ tại phần 25.

## 9. Đề nghị thay đổi lịch đã duyệt

Để không làm mất phòng cũ trong khi chờ duyệt, thông tin đề nghị mới được lưu riêng trong BookingChange.

- Chủ lịch chỉ được gửi trước giờ bắt đầu của lịch gốc; thời gian mới cũng phải trong tương lai.
- Mỗi booking có tối đa một đề nghị PENDING; có thể đổi nội dung, giờ, số người, người dự, team, dự án, phòng ban liên quan, thiết bị và phòng mong muốn. meetingType được sửa qua đề nghị; mức riêng tư chỉ được nâng từ STANDARD lên CONFIDENTIAL, không hạ ngược trên cùng hồ sơ để tránh lộ nội dung lịch sử. Phòng ban tổ chức của booking gốc giữ nguyên.
- Booking gốc vẫn APPROVED, vẫn chiếm phòng cũ. Đề nghị mới không giữ phòng mới.
- Đề nghị chứa một ảnh chụp đầy đủ của dữ liệu mới và phiên bản booking gốc; không âm thầm sửa booking gốc.
- Chủ lịch được rút đề nghị trước khi xử lý. Muốn sửa đề nghị phải rút rồi tạo lại.
- Admin khác người tổ chức được duyệt/từ chối; lý do từ chối bắt buộc.
- Khi duyệt: kiểm tra booking gốc còn APPROVED/chưa bắt đầu/đúng phiên bản, tư cách người tổ chức/team/dự án, phòng ban liên quan, dữ liệu mới và phòng được chọn. Với CONFIDENTIAL, admin duyệt phần thay đổi vận hành qua bản hiển thị đã che trường nội dung; không tự động được xem agenda hoặc danh sách người dự. Kiểm tra trùng phải bỏ qua chính booking gốc.
- Thành công: cập nhật booking gốc và trạng thái đề nghị trong một thao tác nguyên tử, giải phóng phòng cũ và chiếm phòng mới đồng thời, lưu trước/sau, thông báo tập hợp người tổ chức và người tham dự cũ/mới.
- Thất bại hoặc bị từ chối: giữ nguyên booking gốc. Từ chối thông báo chủ lịch.
- Khi tới giờ bắt đầu lịch gốc hoặc giờ bắt đầu đề nghị, đề nghị chưa duyệt chuyển EXPIRED; lịch gốc vẫn có hiệu lực.
- Nếu booking gốc bị hủy hoặc được admin chuyển phòng vận hành, đề nghị đang chờ chuyển CANCELLED kèm lý do; người tổ chức có thể tạo đề nghị mới nếu còn đủ thời gian.

Trạng thái BookingChange: PENDING, APPROVED, REJECTED, CANCELLED, EXPIRED. Không mở lại trạng thái kết thúc.

## 10. Báo cáo sự cố

### 10.1. Dữ liệu và quyền

- Nhân viên ACTIVE có thể báo cáo cho phòng tồn tại: loại sự cố, tiêu đề, mô tả, mức ảnh hưởng đề xuất.
- Loại: EQUIPMENT, POWER, CLEANLINESS, OTHER.
- Ảnh hưởng do người báo đề xuất: MINOR, DEGRADED, UNUSABLE; admin xác định ảnh hưởng thực tế.
- Người báo xem hồ sơ mình tạo; admin xem mọi hồ sơ. Danh sách báo cáo không công khai toàn công ty.
- Nhân viên không tự đóng sự cố hoặc chặn phòng. Không xóa báo cáo sau khi tạo; bổ sung diễn biến được ghi lịch sử.

### 10.2. Luồng xử lý

| Trạng thái | Chuyển tiếp | Điều kiện |
|---|---|---|
| OPEN | IN_PROGRESS | Admin nhận xử lý |
| IN_PROGRESS | RESOLVED | Admin ghi kết quả xử lý bắt buộc |
| RESOLVED | IN_PROGRESS | Admin mở xử lý lại, có lý do |
| RESOLVED | CLOSED | Admin kết thúc theo dõi |
| OPEN | CLOSED | Trùng/không hợp lệ; lý do bắt buộc |

- Báo cáo mới thông báo các admin hoạt động, trừ người báo nếu người báo cũng là admin.
- Thay đổi trạng thái/kết quả thông báo người báo; mỗi lần thay đổi là sự kiện riêng.
- Báo cáo không tự sinh RoomBlock. Sự cố nhỏ không nhất thiết chặn phòng.
- RESOLVED/CLOSED không tự gỡ RoomBlock. Admin phải chủ động kết thúc/hủy khoảng chặn nếu muốn mở phòng sớm, có lịch sử thao tác.
- RoomBlock có thể liên kết RoomIssue nhưng cũng có thể được tạo độc lập cho bảo trì dự kiến.

## 11. Chặn phòng và xử lý lịch bị ảnh hưởng

### 11.1. Khoảng chặn

RoomBlock gồm phòng, startAt, endAt, lý do, người tạo, thời điểm tạo, trạng thái ACTIVE/CANCELLED, sự cố liên quan nếu có.

- endAt > startAt; khoảng chặn bắt đầu trong tương lai hoặc chọn bắt đầu ngay bằng thời điểm backend xác nhận. Không tạo khoảng chặn hồi tố; thời điểm phát hiện sự cố trước đó được ghi trong báo cáo sự cố.
- Khoảng chặn được phép qua nhiều ngày. Không chặn theo toàn bộ ngày nếu chỉ bảo trì vài giờ.
- Hai RoomBlock ACTIVE cùng phòng không được giao nhau trong hệ thống; cho phép hai khoảng liền kề.
- Khoảng chặn đã hết hạn tự không còn ảnh hưởng đến các lần tìm trống tương lai; giữ lịch sử.
- Không sửa khoảng chặn tại chỗ. Muốn đổi thời gian: hủy khoảng cũ và tạo khoảng mới trong một thao tác có kiểm tra xung đột, lưu lý do.

### 11.2. Lịch đã duyệt giao với khoảng chặn

1. Admin xem trước các booking còn phần RoomAllocation giữ hiệu lực từ hiện tại trở đi giao với khoảng chặn và các phòng thay thế phù hợp.
2. Admin chọn xử lý cho từng booking: chuyển phòng hoặc hủy có lý do.
3. Khi xác nhận, backend kiểm tra lại toàn bộ danh sách ảnh hưởng và phiên bản dữ liệu; danh sách xem trước không giữ chỗ.
4. Chỉ tạo RoomBlock nếu tất cả booking bị ảnh hưởng đã có phương án hợp lệ. Tạo chặn và áp dụng mọi phương án trong cùng thao tác nguyên tử; một phần thất bại thì toàn bộ không được áp dụng.
5. Sau thành công mới phát thông báo đến người tổ chức và người dự của từng lịch bị chuyển/hủy.

- Chuyển phòng giữ nguyên giờ dự kiến, người dự, số người, thiết bị và trạng thái APPROVED; phòng mới phải đáp ứng nhu cầu. Với IN_USE, chia đoạn sử dụng tại thời điểm chuyển và chỉ giữ phòng mới cho phần thời gian còn lại, không ghi đè phòng cũ trong lịch sử. Với NOT_CHECKED_IN, không gia hạn hạn check-in. Không chuyển phiên đã COMPLETED/INTERRUPTED/NO_SHOW; chi tiết phần 25.
- Admin không được âm thầm đổi giờ để tránh xung đột.
- Tất cả quyết định chuyển phòng/hủy đều lưu người xử lý, lý do và dữ liệu trước/sau.
- PENDING chưa có phòng nên không bị tự hủy bởi RoomBlock; admin phải tính khoảng chặn khi duyệt sau đó.
- Thông báo sự cố chung không thay thế dữ liệu RoomBlock và không tự làm phòng hết khả dụng.

## 12. Thông báo

### 12.1. Kênh và cấu trúc

Kênh giao tiếp chính thức là thông báo trong ứng dụng. Client lấy danh sách và số chưa đọc qua API; không phụ thuộc WebSocket hoặc email để hoàn thành nghiệp vụ.

Thông báo có loại, tiêu đề, nội dung, thời điểm phát hành, tác nhân và liên kết định danh đến đối tượng liên quan. Mỗi người nhận có readAt riêng. Nội dung không chứa mật khẩu/token hoặc dữ liệu nhạy cảm không cần thiết.

### 12.2. Ma trận sự kiện

| Sự kiện | Người nhận |
|---|---|
| Gửi/sửa yêu cầu PENDING | Admin hoạt động được phép xử lý |
| Duyệt yêu cầu | Người tổ chức và người tham dự hiện tại |
| Từ chối/hết hạn yêu cầu | Người tổ chức |
| Hủy PENDING | Người tổ chức nếu admin hủy; admin được phép xử lý |
| Hủy APPROVED | Người tổ chức và người tham dự |
| Gửi/rút đề nghị thay đổi | Admin hoạt động được phép xử lý |
| Duyệt thay đổi | Hợp của người tổ chức, người dự cũ và người dự mới |
| Từ chối/hết hạn/hủy đề nghị thay đổi | Người tổ chức |
| Admin chuyển phòng | Người tổ chức và người tham dự |
| Đến giờ bắt đầu, chưa check-in và còn thời hạn | Người tổ chức và người tham dự ACTIVE; nhắc check-in một lần |
| Check-in thành công | Người tổ chức và người tham dự; ghi rõ người xác nhận |
| Check-out/kết thúc tự động | Người tổ chức và người tham dự; phân biệt người xác nhận và kết thúc theo giờ |
| Tự hủy NO_SHOW | Người tổ chức, người tham dự và admin ACTIVE; nêu giờ giải phóng và lý do |
| Báo sự cố | Admin hoạt động, trừ chính người báo |
| Cập nhật xử lý sự cố | Người báo |
| Thông báo chung | Danh sách người nhận được xác định khi phát hành |

Thông báo cho người bị loại khỏi danh sách tham dự chỉ cần cho biết họ không còn thuộc cuộc họp; không gửi chi tiết mới. Nếu họ đã mất quyền xem booking, màn hình chi tiết phải từ chối truy cập nhưng vẫn đọc được thông báo tối thiểu này.

### 12.3. Thông báo chung

- Chỉ admin đăng; phạm vi: toàn công ty, một/nhiều phòng ban, một/nhiều team, một/nhiều dự án hoặc người của các booking bị ảnh hưởng được chọn. Với booking CONFIDENTIAL, gửi theo tập người dự thay vì toàn bộ team/dự án/phòng ban và chỉ phát nội dung vận hành đã che trường theo phần 27.
- Có tiêu đề, nội dung và đối tượng liên quan tùy chọn; kiểm tra quyền và phạm vi trước khi phát hành.
- Chốt danh sách tài khoản ACTIVE nhận thông báo tại thời điểm phát hành, loại trùng theo userId. Nhân viên vào phòng ban/dự án sau đó không tự nhận thông báo cũ.
- Thông báo chung phát hành ngay, không hẹn giờ; nội dung đã phát hành không sửa/xóa âm thầm. Muốn đính chính phải phát thông báo mới liên kết bản cũ.

### 12.4. Độ tin cậy và quyền truy cập

- Lưu sự kiện cần thông báo cùng giao dịch với thay đổi nghiệp vụ; xử lý tạo thông báo sau khi giao dịch thành công.
- Nếu tạo/phân phối thông báo lỗi, không hoàn tác quyết định duyệt/hủy; hệ thống thử lại và ghi nhận lỗi để admin vận hành kiểm tra.
- Dùng định danh sự kiện và người nhận để bảo đảm xử lý lại không sinh trùng. Một thay đổi thực sự tiếp theo vẫn tạo sự kiện mới.
- Không phát thông báo thành công cho giao dịch đã thất bại.
- Một người nằm ở nhiều nhóm người nhận chỉ nhận một bản cho một sự kiện.
- Người dùng chỉ liệt kê/đọc/đánh dấu thông báo của mình. Đánh dấu đã đọc không đổi trạng thái booking/sự cố.
- Liên kết từ thông báo luôn kiểm tra quyền hiện tại tại lúc mở.
- Không xóa cứng thông báo trong hệ thống. Đọc tất cả chỉ đánh dấu các thông báo đã tồn tại tới mốc yêu cầu, không đánh dấu các bản mới đến sau đó.

## 13. Thống kê cơ bản

- Admin xem số yêu cầu theo trạng thái, số lịch được duyệt/hủy và số giờ theo phòng, phòng ban, team, dự án, loại cuộc họp. CONFIDENTIAL tuân thủ giới hạn báo cáo ở phần 27.
- Phòng ban được tính theo đơn vị tổ chức đã ghi nhận trên booking; dự án theo liên kết được duyệt của booking.
- Tách số giờ theo lịch dự kiến, số giờ giữ phòng hiệu lực, thời gian sử dụng được xác nhận, thời gian kết thúc theo suy định và thời gian được giải phóng sớm. Công thức và nguồn dữ liệu tại phần 26.
- Lịch CANCELLED/NO_SHOW được đếm riêng; việc hủy sau khi đã sử dụng không xóa đoạn sử dụng trước đó.
- Giữ dữ liệu lịch đã từng được duyệt và chuyển phòng trong allocation/audit; không dùng một mình assignedRoomId hiện tại để tính lịch sử.

## 14. Dữ liệu logic và lịch sử

| Đối tượng | Thuộc tính/quan hệ chính |
|---|---|
| User | employeeCode, email, tên, departmentId, departmentRole (HEAD/MEMBER), systemRole, status |
| Department | code, name, status |
| Project | code, name, status |
| ProjectMember | projectId, userId, projectRole, status |
| Team | code, name, description, status ACTIVE/ARCHIVED |
| TeamMember | teamId, userId, teamRole LEAD/MEMBER, status; duy nhất theo cặp |
| BookingDepartment | bookingId, departmentId; duy nhất theo cặp, lưu ảnh chụp theo phiên bản |
| Room | code, name, location, capacity, status |
| Equipment | code, name, status |
| RoomEquipment | roomId, equipmentId, quantity |
| Booking | organizerId, organizerDepartmentId tại lúc gửi, organizingTeamId, projectId, meetingType, visibility, title, purpose, startAt, endAt, attendeeCount, preferredRoomId, assignedRoomId, status, cancellationReasonCode, version |
| BookingUsage | bookingId duy nhất, status, checkInDeadline, checkedInAt/By, checkedOutAt/By, effectiveEndedAt, endSource, version |
| RoomAllocation | bookingId, roomId, fromAt, untilAt, releasedAt, releaseReason; giữ lịch sử từng lần bố trí |
| UsageSegment | bookingId, allocationId, roomId, startedAt, endedAt, endSource, endedBy |
| UsageEvent | bookingId, loại sự kiện, tác nhân, thời điểm hiệu lực/thời điểm xử lý, evidenceType, lý do |
| BookingParticipant | bookingId, userId; duy nhất theo cặp |
| BookingEquipmentRequirement | bookingId, equipmentId, quantity |
| BookingChange | bookingId, baseVersion của booking gốc, version riêng, dữ liệu đề xuất, status, người/giờ xử lý, lý do |
| RoomIssue | roomId, reporterId, category, title, description, impact, status, resolution, version |
| RoomBlock | roomId, startAt, endAt, status, reason, issueId tùy chọn, version |
| Notification | eventId, type, title, content, relatedType, relatedId, publishedAt |
| NotificationRecipient | notificationId, userId, readAt; duy nhất theo cặp |
| Announcement | Người phát hành, nội dung, phạm vi, thời điểm và danh sách người nhận đã chốt |
| AuditEvent | Đối tượng, hành động, tác nhân, thời điểm, lý do, trước/sau, requestId |
| OutboxEvent | Sự kiện nghiệp vụ chờ xử lý thông báo, trạng thái thử lại |

Mỗi hồ sơ nghiệp vụ có id, createdAt, updatedAt; các quyết định có người/giờ xử lý và lý do khi bắt buộc. Audit chỉ ghi thêm, không sửa/xóa qua API thông thường. Lịch sử giữ giá trị hiển thị cần thiết tại thời điểm quyết định để việc đổi tên danh mục không làm mất ngữ cảnh.

Tài liệu này là mô hình logic; không bắt buộc mỗi dòng tương ứng một bảng vật lý. Dữ liệu phiên bản đề nghị và audit phải đủ phục hồi nội dung quyết định, không chỉ lưu chuỗi mô tả chung chung.

## 15. Tính nhất quán và xử lý đồng thời

- Không có hai RoomAllocation chiếm phòng hiệu lực giao nhau trên cùng phòng; không có phần chiếm phòng hiệu lực giao RoomBlock ACTIVE. Kiểm tra dựa trên khoảng allocation sau giải phóng, không chỉ so giờ dự kiến của Booking. Các đoạn quá khứ được giữ để truy vết.
- Bảo đảm bất biến cả khi duyệt, duyệt thay đổi, chuyển phòng, tạo block, check-in/check-out, tự hủy và cập nhật danh mục chạy đồng thời. Kiểm tra ở giao diện hoặc truy vấn trước khi ghi là chưa đủ.
- Mọi thao tác thay đổi trạng thái phải nguyên tử, kèm kiểm tra trạng thái hiện tại và phiên bản hồ sơ. Hai admin xử lý cùng hồ sơ: chỉ một quyết định thành công; bên còn lại nhận xung đột và tải lại.
- Ràng buộc booking–block cần một cơ chế ghi/khóa thống nhất theo phòng; không giả định ràng buộc trùng booking tự bảo vệ được một bảng block độc lập. Chi tiết lựa chọn thuộc tài liệu thiết kế kỹ thuật.
- Thao tác nhiều phòng cần thứ tự xử lý nhất quán để tránh các giao dịch giữ tài nguyên chéo nhau.
- Các API tạo yêu cầu, đề nghị thay đổi, báo cáo sự cố, check-in/check-out và phát thông báo chung hỗ trợ Idempotency-Key: cùng người, cùng thao tác, cùng key và cùng nội dung trả lại kết quả cũ; cùng key khác nội dung trả xung đột. Lưu key ít nhất 24 giờ; gửi lại sau thời hạn không được bảo đảm chống trùng.
- Thông báo và audit phải gắn với quyết định đã commit; không ghi một phần thay đổi phòng mà bỏ sót trạng thái hồ sơ.

## 16. Hợp đồng chức năng API

Các endpoint dưới đây là giao diện chức năng phải được cung cấp. Tiền tố /api/v1 là phiên bản giao tiếp API, không phải giới hạn phạm vi sản phẩm. Hợp đồng JSON chung và dữ liệu của các thao tác quan trọng được quy định ở phần 23; tài liệu OpenAPI khi triển khai phải tuân thủ spec này.

| Nhóm | Endpoint tiêu biểu | Mục đích |
|---|---|---|
| Xác thực | POST /api/v1/auth/login; GET /api/v1/me | Đăng nhập, danh tính/quyền hiện tại |
| Nhân sự | /api/v1/users; /departments; /projects; /projects/{id}/members | Admin quản lý, nhân viên tra cứu dữ liệu được phép |
| Phòng | GET/POST /api/v1/rooms; PATCH /rooms/{id} | Tra cứu/quản lý phòng |
| Thiết bị | /api/v1/equipment; PUT /rooms/{id}/equipment | Danh mục và cấu hình thiết bị phòng |
| Tìm trống | GET /api/v1/rooms/availability | Lọc thời gian, số người, thiết bị |
| Lịch | GET /api/v1/rooms/{id}/calendar; GET /api/v1/me/calendar | Lịch phòng/cá nhân |
| Yêu cầu | POST/GET /api/v1/bookings; GET/PATCH /bookings/{id} | Gửi, liệt kê theo quyền, xem, sửa PENDING |
| Quyết định | POST /bookings/{id}/approve; /reject; /cancel | Duyệt, từ chối, hủy với phiên bản và lý do phù hợp |
| Thay đổi | POST /bookings/{id}/changes; GET /bookings/{id}/changes | Tạo/xem đề nghị |
| Xử lý thay đổi | POST /booking-changes/{id}/approve; /reject; /withdraw | Duyệt/từ chối/rút đề nghị |
| Sự cố | POST/GET /api/v1/room-issues; GET /room-issues/{id}; POST /room-issues/{id}/transitions | Báo cáo và chuyển trạng thái có kiểm tra |
| Khoảng chặn | POST /api/v1/room-blocks/preview; POST /room-blocks; POST /room-blocks/{id}/cancel | Xem ảnh hưởng, tạo kèm phương án xử lý, hủy chặn |
| Đổi khoảng chặn | POST /api/v1/room-blocks/{id}/replace | Hủy cũ/tạo mới nguyên tử, kèm kế hoạch xử lý |
| Chuyển phòng | POST /api/v1/bookings/{id}/reassign-room | Admin chuyển phòng do vận hành, có lý do |
| Thông báo | GET /api/v1/me/notifications; GET /me/notifications/unread-count; POST /me/notifications/{id}/read; POST /me/notifications/read-all | Hộp thông báo cá nhân |
| Thông báo chung | POST /api/v1/announcements | Admin phát hành theo phạm vi |
| Thống kê | GET /api/v1/reports/bookings | Báo cáo theo khoảng thời gian và nhóm |
| Lịch sử | GET /api/v1/bookings/{id}/history; GET /room-issues/{id}/history | Theo quyền của đối tượng, ẩn dữ liệu quản trị không được phép |

Các đường dẫn rút gọn trong cùng hàng kế thừa tiền tố /api/v1. GET bookings mặc định chỉ trả hồ sơ người dùng được xem; quyền ADMIN và bộ lọc quyết định việc xem toàn bộ. Không có API cập nhật status tùy ý để bỏ qua nghiệp vụ.

### 16.1. Quy ước lỗi và phân trang

| HTTP | Tình huống |
|---|---|
| 400 | Dữ liệu thiếu/sai định dạng, khoảng thời gian hoặc số người không hợp lệ |
| 401 | Chưa đăng nhập hoặc thông tin xác thực không còn hợp lệ |
| 403 | Không có quyền thao tác; tài khoản bị khóa không được tiếp tục sử dụng |
| 404 | Đối tượng không tồn tại; có thể dùng để che đối tượng không được phép biết |
| 409 | Trùng phòng, sai trạng thái, phiên bản cũ, dữ liệu tham chiếu thay đổi, key gửi lại khác nội dung |

Lỗi có code ổn định, message dễ hiểu, fieldErrors nếu có, requestId; không trả stack trace. Danh sách phân trang mặc định 20, tối đa 100; bộ lọc/sắp xếp có danh sách cho phép. Phản hồi duyệt thành công trả phòng chính thức và phiên bản mới.

## 17. Tiêu chí nghiệm thu

| ID | Tình huống | Kết quả bắt buộc |
|---|---|---|
| AC-01 | Nhân viên gửi yêu cầu hợp lệ | PENDING, chưa giữ phòng, admin có thông báo |
| AC-02 | Hai yêu cầu chờ cùng phòng/giờ | Cả hai được nhận; không khẳng định đã có phòng |
| AC-03 | Admin duyệt khi phòng còn phù hợp | APPROVED, phòng chính thức, audit và thông báo |
| AC-04 | Hai admin duyệt hai yêu cầu trùng đồng thời | Chỉ một được duyệt; yêu cầu còn lại vẫn PENDING |
| AC-05 | Hai lịch 09:00–10:00 và 10:00–11:00 | Được phép cùng phòng |
| AC-06 | Admin duyệt chính yêu cầu/đề nghị của mình | Bị từ chối |
| AC-07 | Chủ yêu cầu sửa trong lúc admin duyệt bản cũ | Một thao tác thắng; thao tác còn lại nhận xung đột, không ghi đè |
| AC-08 | Yêu cầu đến giờ bắt đầu mà chưa duyệt | Không duyệt được, EXPIRED, thông báo một lần |
| AC-09 | Duyệt phòng thiếu chỗ/thiết bị hoặc đang bị chặn | Bị từ chối, không ghi một phần |
| AC-10 | Người ngoài dự án gắn booking với dự án | Bị từ chối |
| AC-11 | Người tổ chức rời dự án trước khi duyệt | Không duyệt được cho tới khi điều chỉnh hợp lệ |
| AC-12 | Đổi phòng ban của người tổ chức | Booking cũ giữ đơn vị tổ chức ban đầu |
| AC-13 | Người không liên quan xem chi tiết cuộc họp | Không xem được; vẫn thấy giờ bận công khai |
| AC-14 | Gửi đề nghị thay đổi APPROVED | Lịch cũ giữ phòng, dữ liệu mới chưa thay thế |
| AC-15 | Từ chối/duyệt thay đổi thất bại do xung đột | Lịch cũ giữ nguyên |
| AC-16 | Duyệt thay đổi hợp lệ | Đổi lịch nguyên tử, thông báo người dự cũ/mới phù hợp |
| AC-17 | Đề nghị chưa duyệt đến hạn hoặc lịch gốc bị hủy | Đề nghị hết hạn/hủy tương ứng, không được áp dụng sau đó |
| AC-18 | Chủ lịch hủy trước giờ bắt đầu | Giải phóng phòng, lưu lý do, thông báo đúng người |
| AC-19 | Chủ lịch hủy sau khi bắt đầu | Bị từ chối; admin vẫn có thể hủy trước khi kết thúc |
| AC-20 | Tạo báo cáo hỏng máy chiếu | OPEN, admin nhận tin; không tự chặn cả phòng |
| AC-21 | Tạo block có lịch ảnh hưởng nhưng thiếu phương án | Không tạo block, trả danh sách cần xử lý |
| AC-22 | Một phương án chuyển phòng trong kế hoạch bị xung đột | Không áp dụng bất kỳ phần nào của kế hoạch |
| AC-23 | Duyệt booking đồng thời tạo block cùng phòng/giờ | Không thể tồn tại cả hai kết quả xung đột |
| AC-24 | Phòng bảo trì 14:00–16:00 | Không duyệt giao khoảng đó; khung khác vẫn được đặt |
| AC-25 | Đóng sự cố còn RoomBlock | Không tự mở phòng sớm; phải xử lý khoảng chặn riêng |
| AC-26 | Ngừng phòng/giảm sức chứa làm hỏng lịch tương lai | Bị chặn, có danh sách lịch ảnh hưởng |
| AC-27 | Tạo thông báo lỗi sau khi duyệt thành công | Booking vẫn APPROVED; thử lại không tạo trùng |
| AC-28 | Một người thuộc nhiều nhóm nhận | Một bản thông báo cho một sự kiện |
| AC-29 | Người dùng đọc thông báo của người khác | Bị từ chối |
| AC-30 | Gửi lại cùng Idempotency-Key trong thời hạn | Không tạo thêm đối tượng/sự kiện |
| AC-31 | Phát thông báo bảo trì nhưng không tạo RoomBlock | Không thay đổi trạng thái trống của phòng |
| AC-32 | Gửi sự cố/chặn phòng hoặc xem lịch qua ranh giới ngày | Mốc thời gian hiển thị và giao nhau đúng múi giờ nghiệp vụ |

## 18. Yêu cầu vận hành và bàn giao

- Khởi động toàn bộ ứng dụng và PostgreSQL bằng `docker compose up` theo README.
- Có migration có phiên bản, dữ liệu mẫu phòng ban/dự án/phòng/thiết bị và tối thiểu hai admin cho môi trường demo; không ghi mật khẩu sản xuất vào mã nguồn.
- README nêu phạm vi, các chính sách nghiệp vụ, cách chạy, cấu hình, tài khoản demo, cách kiểm thử và lựa chọn bảo vệ đặt trùng.
- Có hợp đồng API để kiểm tra các luồng chính; log gắn requestId, không ghi mật khẩu/token.
- Kiểm thử tập trung vào phân quyền, chuyển trạng thái, ranh giới thời gian, xử lý đồng thời và kế hoạch đổi phòng/chặn phòng nguyên tử.
- Quy mô 200 nhân viên là số tài khoản mục tiêu, không đồng nghĩa 200 yêu cầu đồng thời. Các tiêu chí hiệu năng và vận hành được quy định tại phần 24.
- Không cung cấp thao tác xóa lịch sử nghiệp vụ qua API. Chính sách sao lưu và lưu trữ được quy định tại phần 24.

## 19. Bộ chính sách nghiệp vụ áp dụng

Các lựa chọn dưới đây là quy tắc cụ thể của tài liệu, làm căn cứ triển khai và kiểm thử. Thay đổi chính sách phải được phản ánh vào spec và kiểm thử; không tự suy ra thêm quyền từ chức danh.

| Mã | Chính sách |
|---|---|
| POL-01 | Mọi yêu cầu cần một admin khác người tổ chức duyệt; không duyệt nhiều cấp |
| POL-02 | PENDING không giữ phòng; lịch APPROVED giữ theo RoomAllocation; check-out/NO_SHOW có thể giải phóng sớm; RoomBlock ACTIVE chặn theo khoảng |
| POL-03 | Người tổ chức chịu trách nhiệm tạo/sửa yêu cầu; không đặt hộ hoặc chuyển chủ lịch |
| POL-04 | Đơn vị chủ trì được ghi nhận lúc gửi; team và dự án tùy chọn, độc lập, phải đúng tư cách; cho phép nhiều phòng ban phối hợp |
| POL-05 | Cho phép họp mọi ngày/giờ, tối thiểu một phút, không qua ranh giới ngày; được kết thúc đúng 00:00 ngày tiếp theo |
| POL-06 | Không giới hạn ngày đặt trước; không có thời gian đệm |
| POL-07 | Phòng phải đủ số người và thiết bị; không đặt chung một phòng cho nhiều cuộc họp đồng thời |
| POL-08 | Không cấp ưu tiên theo chức danh/phòng ban; gửi trước được ưu tiên xử lý khi tương đương |
| POL-09 | Không chặn trùng lịch cá nhân của người tham dự |
| POL-10 | Sửa APPROVED qua đề nghị riêng; giữ nguyên lịch cũ đến lúc duyệt thành công |
| POL-11 | Chủ lịch không hủy/sửa khi đã bắt đầu; admin có thể chuyển phòng/hủy do vận hành trước khi kết thúc |
| POL-12 | Hết giờ bắt đầu mà chưa duyệt thì EXPIRED; không có cam kết riêng về thời hạn admin phản hồi |
| POL-13 | Thông báo trong ứng dụng; nhắc check-in tại giờ bắt đầu; không nhắc trước giờ họp hoặc gửi ra email/SMS |
| POL-14 | Báo sự cố không tự chặn phòng; đóng sự cố không tự gỡ RoomBlock |
| POL-15 | Chặn phòng phải giải quyết đầy đủ lịch ảnh hưởng bằng một thao tác nguyên tử |
| POL-16 | Tài khoản và cơ cấu tổ chức do admin quản lý nội bộ |
| POL-17 | Không xóa cứng lịch sử; đổi/hủy không được làm mất bằng chứng quyết định |
| POL-18 | Check-in từ giờ bắt đầu tới trước min(startAt + 10 phút, endAt); không check-in sớm hoặc hồi tố |
| POL-19 | Quá hạn chưa check-in tự hủy NO_SHOW, giải phóng phần còn lại; admin không khôi phục bằng cách check-in muộn |
| POL-20 | Check-out sớm giải phóng phòng ngay; tới endAt tự đóng phiên và phân biệt kết thúc theo suy định |
| POL-21 | Xác nhận sử dụng bằng thao tác tài khoản hợp lệ; không tuyên bố bằng chứng hiện diện vật lý hoặc điểm danh cá nhân |
| POL-22 | Không bắt buộc team/dự án; mục đích họp độc lập với cơ cấu tổ chức |
| POL-23 | Phỏng vấn, review lương và đánh giá cá nhân bắt buộc CONFIDENTIAL; admin vận hành không mặc nhiên xem nội dung riêng |

## 20. Truy vết yêu cầu và điều kiện hoàn thành

| Mã yêu cầu | Nhu cầu | Phần đặc tả | Nghiệm thu chính |
|---|---|---|---|
| REQ-01 | Quản lý tài khoản, phòng ban, dự án và quyền | 4, 5, 21 | AC-06, AC-10–13, AC-33–38 |
| REQ-02 | Quản lý phòng, thiết bị | 6, 21 | AC-09, AC-26, AC-39 |
| REQ-03 | Tìm phòng trống và xem lịch | 7 | AC-05, AC-13, AC-24, AC-32 |
| REQ-04 | Gửi và phê duyệt yêu cầu | 8, 22 | AC-01–08, AC-11, AC-18–19 |
| REQ-05 | Đổi lịch an toàn | 9 | AC-14–17 |
| REQ-06 | Xử lý sự cố và lịch ảnh hưởng | 10, 11 | AC-20–26 |
| REQ-07 | Thông báo và phát tin theo nhóm | 12 | AC-27–31, AC-40–42 |
| REQ-08 | Thống kê và lịch sử | 13, 14 | AC-12, AC-43 |
| REQ-09 | Tính nhất quán, gửi lại, xử lý đồng thời | 15 | AC-04, AC-07, AC-22–23, AC-27, AC-30 |
| REQ-10 | API, bảo mật và vận hành | 16, 18, 23, 24 | AC-33–35, AC-44–46 |
| REQ-11 | Xác nhận sử dụng, giải phóng phòng, tự hủy không đến | 25 | AC-47–63 |
| REQ-12 | Thống kê sử dụng có bằng chứng và suy định | 26 | AC-64–66 |
| REQ-13 | Team, họp liên phòng ban, nhiều mục đích và riêng tư | 27 | AC-67–76 |

Hệ thống hoàn thành khi tất cả yêu cầu trong phạm vi có API thực hiện được, tiêu chí nghiệm thu đạt, migration chạy được trên cơ sở dữ liệu mới và dữ liệu hiện có hợp lệ, Docker Compose khởi động được, có tài liệu sử dụng/kiểm thử và không còn lỗi làm vi phạm các bất biến tại phần 15. Một bảng hoặc endpoint tồn tại nhưng không bảo vệ quy tắc nghiệp vụ chưa được coi là hoàn thành.

## 21. Quản trị tài khoản và danh mục

### 21.1. Đăng nhập và phiên làm việc

- Đăng nhập bằng email và mật khẩu; email bỏ khoảng trắng đầu/cuối và chuyển chữ thường trước khi so sánh tính duy nhất. Mã nhân viên/mã danh mục chuẩn hóa về chữ hoa; không tái sử dụng mã của hồ sơ đã ngừng hoạt động.
- Mật khẩu tối thiểu 12, tối đa 128 ký tự; không loại bỏ khoảng trắng hoặc tự đổi nội dung mật khẩu. Chỉ lưu giá trị băm phù hợp cho mật khẩu, không có API đọc lại mật khẩu.
- Admin tạo tài khoản với mật khẩu tạm được cung cấp trong request qua kết nối bảo mật; nhân viên bắt buộc đổi mật khẩu tại lần đăng nhập đầu. Trước khi đổi, chỉ được truy cập danh tính, đổi mật khẩu và đăng xuất.
- Nhân viên tự đổi mật khẩu phải cung cấp mật khẩu hiện tại. Admin có thể đặt lại mật khẩu tạm và bật yêu cầu đổi mật khẩu, có audit; không gửi mật khẩu trong Notification.
- Phiên truy cập có hạn 15 phút, phiên làm mới có hạn tuyệt đối 7 ngày tính từ đăng nhập. Làm mới phải thay token làm mới; token cũ không được sử dụng lại. Không gia hạn vượt hạn tuyệt đối của phiên.
- Đăng xuất thu hồi phiên hiện tại; đổi/reset mật khẩu hoặc khóa tài khoản thu hồi mọi phiên của tài khoản. Quyền và trạng thái hiện tại vẫn phải được kiểm tra khi thao tác, không chờ token tự hết hạn.
- Sai email/mật khẩu trả thông báo chung, không tiết lộ email có tồn tại. Giới hạn 5 lần đăng nhập sai trong 15 phút cho cặp email chuẩn hóa và nguồn truy cập; vượt giới hạn trả 429 cùng thời điểm được thử lại.
- Ghi nhận sự kiện đăng nhập, đăng xuất, khóa tài khoản và thay đổi quyền; không ghi token/mật khẩu vào log.

### 21.2. Quản lý tài khoản

- Admin tạo/sửa tên, phòng ban, vai trò phòng ban, vai trò hệ thống, trạng thái. Tên dài 1–150 ký tự; email tối đa 254 ký tự; mã nhân viên 1–50 ký tự.
- Không sửa email/mã nhân viên qua chức năng cập nhật thông thường; thay đổi định danh cần quy trình quản trị riêng ngoài phạm vi hệ thống.
- Tạo/kích hoạt người dùng yêu cầu phòng ban ACTIVE. Admin cũng phải thuộc một phòng ban.
- Không khóa hoặc hạ quyền admin ACTIVE cuối cùng. Mọi thao tác đổi quyền phải xét đồng thời để hai admin không cùng loại bỏ quyền quản trị cuối cùng.
- Khóa người dùng là INACTIVE, giữ hồ sơ và liên kết lịch sử. Không tự gỡ họ khỏi cuộc họp/dự án hoặc tự thay người tổ chức.
- Khi duyệt booking hoặc thay đổi danh sách người dự, danh sách người dự mới phải là tài khoản ACTIVE. Nếu người dự đã bị khóa trước khi duyệt, trả danh sách không hợp lệ để người tổ chức sửa; không âm thầm bỏ tên.

### 21.3. Phòng ban và dự án

- Phòng ban có ACTIVE/INACTIVE; dự án có ACTIVE/ARCHIVED. Mã 1–50 ký tự, tên 1–150 ký tự; mã duy nhất trong từng loại danh mục.
- Không ngừng phòng ban còn nhân viên ACTIVE; phải chuyển nhân viên sang phòng ban khác trước. Booking cũ vẫn giữ liên kết phòng ban cũ.
- Chỉ nhân viên ACTIVE mới được thêm/kích hoạt thành viên dự án; không thêm thành viên mới vào dự án ARCHIVED. Không bắt buộc dự án luôn có MANAGER để hệ thống tiếp nhận dữ liệu tổ chức đang chuyển giao.
- Rời dự án là vô hiệu hóa thành viên, không xóa liên kết lịch sử. Vai trò hoặc trạng thái thành viên thay đổi phải được ghi audit.
- Bổ nhiệm HEAD thứ hai cho cùng phòng ban bị từ chối, trừ thao tác thay trưởng phòng đồng thời hạ HEAD cũ xuống MEMBER. Có thể bỏ trống vị trí HEAD.
- ARCHIVED có thể được admin kích hoạt lại; việc này không tự khôi phục thành viên đã bị vô hiệu hóa.

### 21.4. Thiết bị và khả năng sử dụng

- Equipment có ACTIVE/INACTIVE; chỉ loại ACTIVE được chọn cho yêu cầu mới.
- Ngừng loại thiết bị bị chặn nếu còn yêu cầu PENDING, đề nghị PENDING hoặc lịch còn phần RoomAllocation giữ hiệu lực từ hiện tại trở đi đòi hỏi loại đó. Admin phải xử lý các hồ sơ liên quan trước.
- Giảm số lượng thiết bị sử dụng được trong phòng phải kiểm tra lịch còn phần RoomAllocation giữ hiệu lực từ hiện tại trở đi. Nếu không thể đáp ứng, phải giải quyết lịch ảnh hưởng trước khi ghi nhận giảm.
- Sự cố thiết bị không tự cập nhật số lượng sử dụng được. Admin xác minh và thực hiện xử lý danh mục hoặc chặn phòng, bảo đảm không giữ trạng thái phòng đáp ứng nhu cầu khi đã xác nhận thiết bị bắt buộc không sử dụng được.

## 22. Đặc tả các ca sử dụng trọng tâm

### UC-01. Nhân viên gửi yêu cầu và được bố trí phòng

**Tiền điều kiện:** tài khoản ACTIVE, đã đổi mật khẩu tạm, thuộc phòng ban ACTIVE; dữ liệu dự án hợp lệ nếu có.

**Luồng chính:** tìm phòng → nhập nhu cầu → gửi yêu cầu → nhận mã PENDING → admin xem hồ sơ hiện hành → chọn phòng đáp ứng → duyệt → người tổ chức/người dự nhận thông báo → lịch phòng hiển thị cuộc họp.

**Nhánh ngoại lệ:** hết phòng lúc duyệt thì giữ PENDING, trả ROOM_UNAVAILABLE; người tổ chức sửa dữ liệu trong lúc duyệt thì trả VERSION_CONFLICT; đến giờ bắt đầu thì EXPIRED; dữ liệu dự án/người dự không hợp lệ thì yêu cầu sửa hồ sơ. Không có nhánh lỗi nào tự phê duyệt thiếu điều kiện.

**Hậu điều kiện thành công:** một lịch chính thức có đúng một phòng; lưu đầy đủ người/giờ duyệt và phiên bản; không vi phạm sức chứa, thiết bị hoặc lịch.

### UC-02. Người tổ chức đề nghị đổi lịch

**Tiền điều kiện:** booking APPROVED chưa bắt đầu, không có BookingChange PENDING.

**Luồng chính:** xem lịch gốc → gửi toàn bộ thông tin đề xuất và baseVersion → lưu BookingChange PENDING → admin khác người tổ chức xem trước/sau → chọn phòng → duyệt nguyên tử → cập nhật lịch gốc và thông báo.

**Nhánh ngoại lệ:** phòng mới không còn trống, dữ liệu mới không hợp lệ hoặc admin từ chối thì lịch gốc không đổi. Nếu lịch gốc đã bị chuyển phòng hoặc hủy, đề nghị bị đóng; nếu đã bắt đầu thì hết hạn.

**Hậu điều kiện:** không tồn tại thời điểm giao dịch đã commit mà người dùng mất lịch cũ nhưng chưa được cấp lịch mới.

### UC-03. Admin xử lý phòng không sử dụng được

**Tiền điều kiện:** admin ACTIVE đã xác định phòng và khoảng ảnh hưởng; có thể bắt nguồn từ RoomIssue.

**Luồng chính:** xem trước ảnh hưởng → chọn chuyển/hủy từng lịch → xác nhận cùng phiên bản dữ liệu → kiểm tra lại tất cả phòng và lịch → áp dụng kế hoạch cùng RoomBlock → lưu audit → phát thông báo từng cuộc họp.

**Nhánh ngoại lệ:** xuất hiện lịch mới sau xem trước, phòng thay thế bị chiếm hoặc thiếu một phương án thì trả IMPACT_PLAN_STALE/INCOMPLETE; không ghi bất kỳ phần nào. Admin tải lại danh sách và lập kế hoạch mới.

**Hậu điều kiện:** phòng bị chặn không còn khoảng RoomAllocation chiếm phòng hiệu lực giao với khoảng chặn; tất cả lịch thay thế đáp ứng yêu cầu. Không phải chờ thông báo được đọc mới áp dụng kế hoạch.

### UC-04. Admin phát thông báo cho nhóm

**Tiền điều kiện:** admin ACTIVE, nội dung hợp lệ, phạm vi nhận tồn tại.

**Luồng chính:** chọn phạm vi → hệ thống xem trước số tài khoản ACTIVE duy nhất → admin phát hành → chốt lại tập người nhận tại thời điểm phát hành → tạo thông báo cá nhân → từng người đọc độc lập.

**Nhánh ngoại lệ:** tập người nhận thay đổi sau xem trước thì dùng tập ở thời điểm phát hành và trả số nhận thực tế; không còn người nhận thì từ chối với EMPTY_AUDIENCE. Gửi lại cùng key không phát lần hai.

**Hậu điều kiện:** nội dung và danh sách nhận đã phát hành có thể truy vết; không tự thay đổi lịch hoặc trạng thái phòng.

### UC-05. Nhân viên báo và theo dõi sự cố

**Tiền điều kiện:** nhân viên ACTIVE, phòng tồn tại kể cả đang INACTIVE.

**Luồng chính:** nhập sự cố → tạo OPEN → admin nhận tin → nhận xử lý IN_PROGRESS → ghi biện pháp/kết quả RESOLVED → kết thúc CLOSED; người báo nhận các cập nhật.

**Nhánh ngoại lệ:** báo cáo trùng/không hợp lệ được admin đóng từ OPEN kèm lý do; tái phát trước CLOSED thì mở lại IN_PROGRESS từ RESOLVED. CLOSED là kết thúc, phát sinh mới cần tạo báo cáo mới liên kết báo cáo cũ nếu có.

**Hậu điều kiện:** có lịch sử đầy đủ; quyết định mở/chặn phòng vẫn theo mục 11, không tự suy ra từ nhãn trạng thái sự cố.

## 23. Hợp đồng dữ liệu và lỗi nghiệp vụ

### 23.1. Quy ước chung

- Định danh là chuỗi UUID; client không dự đoán tính liên tiếp. Mã hiển thị nghiệp vụ tách khỏi id nội bộ khi cần.
- Thời gian là chuỗi ISO 8601 có offset; response chuẩn hóa +07:00 cho trường phục vụ hiển thị nghiệp vụ. Database không sử dụng chuỗi giờ địa phương làm nguồn xác định xung đột.
- Các thao tác trên hồ sơ hiện có gửi `expectedVersion` số nguyên không âm; backend so với phiên bản đã lưu, không chỉ so timestamp.
- PATCH chỉ nhận các trường được phép sửa ở trạng thái đó; các trường organizerId, assignedRoomId, status, approvedBy không được ghi tùy ý. Trường không được phép gửi trả 400 FIELD_NOT_WRITABLE.
- Chuỗi mô tả là văn bản thuần, không hỗ trợ HTML. Sau trim không được rỗng nếu bắt buộc; title tối đa 200, description/purpose/note/reason/resolution tối đa 2.000 ký tự.
- Danh sách id và thiết bị không nhận phần tử trùng. Danh sách người tham dự tối đa tổng tài khoản ACTIVE của công ty; số người có thể lớn hơn số tài khoản vì có khách ngoài.
- Mọi list trả `items`, `page`, `size`, `totalElements`, `totalPages`; page bắt đầu từ 0. Sắp xếp mặc định createdAt giảm dần rồi id để kết quả ổn định, trừ hàng chờ admin là submittedAt tăng dần rồi id.
- Tìm lịch theo khoảng ngày nhận tối đa 366 ngày một lần. Tìm phòng trống chỉ nhận một khoảng cuộc họp hợp lệ; báo cáo được truy vấn tối đa 366 ngày mỗi lần.
- Thành công tạo mới trả 201 và đối tượng; GET và thao tác trạng thái trả 200 cùng kết quả hiện tại. Đánh dấu đã đọc có thể trả 204, thao tác gửi lại không tạo dữ liệu mới.

### 23.2. Dữ liệu các thao tác trọng tâm

| Thao tác | Request bắt buộc | Request tùy chọn | Response chính |
|---|---|---|---|
| Tạo booking | title, purpose, meetingType, startAt, endAt, attendeeCount | visibility, organizingTeamId, relatedDepartmentIds, projectId, preferredRoomId, participantIds, requiredEquipment, note | booking hiện tại, warnings |
| Sửa PENDING | expectedVersion và trường cần đổi | Các trường được phép ở mục 8 | booking hiện tại, warnings |
| Duyệt booking | expectedVersion, roomId | note | APPROVED, assignedRoom, processedBy/At, version |
| Từ chối/hủy | expectedVersion, reason | Không | trạng thái và thông tin xử lý |
| Tạo BookingChange | baseVersion và ảnh chụp đầy đủ dữ liệu đề xuất như tạo booking | Các trường tùy chọn trong ảnh chụp | đề nghị PENDING và lịch gốc không đổi |
| Duyệt BookingChange | expectedVersion của đề nghị, roomId | note | đề nghị APPROVED và booking phiên bản mới |
| Rút đề nghị | expectedVersion của đề nghị, reason | Không | đề nghị CANCELLED, lịch gốc không đổi |
| Báo sự cố | roomId, category, title, description, impact | relatedBookingId | issue OPEN |
| Xử lý sự cố | expectedVersion, targetStatus | resolution/reason theo chuyển trạng thái | issue phiên bản mới |
| Xem trước RoomBlock | roomId, endAt và startAt hoặc immediate=true | issueId | danh sách lịch ảnh hưởng, phiên bản, phòng gợi ý |
| Tạo RoomBlock | dữ liệu xem trước, reason, resolutions | issueId | block và danh sách kết quả xử lý lịch |
| Chuyển phòng | expectedVersion, roomId, reason | Không | booking APPROVED phiên bản mới |
| Phát thông báo chung | title, content, audience | relatedType, relatedId, correctionOfId | announcementId, recipientCount, publishedAt |

`immediate=true` và startAt loại trừ nhau. Với tạo chặn ngay, endAt vẫn phải sau thời điểm xác nhận. Mỗi phần tử resolutions có bookingId, expectedVersion, action REASSIGN/CANCEL và roomId nếu REASSIGN; hủy cần reason riêng hoặc dùng lý do chặn được ghi tường minh. Backend xác minh danh sách đầy đủ, không tin vào kết quả xem trước do client gửi.

`audience` có type ALL/DEPARTMENTS/TEAMS/PROJECTS/BOOKINGS và ids tương ứng; ALL không có ids, các loại còn lại có ít nhất một id hợp lệ. Nội dung thông báo chỉ được gửi khi người nhận có quyền biết thông tin đó; không dùng thông báo chung để công khai nội dung họp riêng.

### 23.3. Ví dụ tạo và duyệt booking

Ví dụ minh họa định dạng, không phải dữ liệu mẫu bắt buộc. Client gửi Idempotency-Key cho thao tác tạo.

```json
{
  "title": "Họp kế hoạch triển khai",
  "meetingType": "PLANNING",
  "visibility": "STANDARD",
  "purpose": "Thống nhất công việc và trách nhiệm tuần tới",
  "startAt": "2026-11-05T09:00:00+07:00",
  "endAt": "2026-11-05T10:00:00+07:00",
  "attendeeCount": 6,
  "projectId": "afc87984-b11c-4ea8-88bc-61f332478ec0",
  "participantIds": [],
  "requiredEquipment": [
    {"equipmentId": "d24510c0-43cd-4df1-95cb-ebfa463c1447", "quantity": 1}
  ]
}
```

Người tổ chức được tự thêm vào participantIds khi lưu. Danh sách rỗng ở request không có nghĩa không có người dự. Admin duyệt bằng:

```json
{
  "expectedVersion": 0,
  "roomId": "a834bbae-ef77-4cc6-840c-45194cab4df2",
  "note": "Bố trí phòng đủ 6 người và có máy chiếu"
}
```

### 23.4. Mã lỗi và cảnh báo

| Code | HTTP | Ý nghĩa |
|---|---|---|
| VALIDATION_FAILED | 400 | Dữ liệu không đáp ứng định dạng/giới hạn |
| FIELD_NOT_WRITABLE | 400 | Client gửi trường không được phép sửa |
| INVALID_TIME_RANGE | 400 | Sai thứ tự giờ, độ chính xác hoặc ranh giới ngày |
| AUTHENTICATION_REQUIRED | 401 | Phiên không hợp lệ hoặc đã thu hồi |
| ACCESS_DENIED | 403 | Không có quyền |
| ACCOUNT_INACTIVE | 403 | Tài khoản đã bị khóa |
| PASSWORD_CHANGE_REQUIRED | 403 | Chưa đổi mật khẩu tạm |
| SELF_APPROVAL_FORBIDDEN | 403 | Tự duyệt/từ chối hồ sơ của mình |
| PROJECT_MEMBERSHIP_REQUIRED | 403 | Không có tư cách dự án để gắn yêu cầu |
| RESOURCE_NOT_FOUND | 404 | Không có đối tượng hoặc không được phép biết |
| ROOM_UNAVAILABLE | 409 | Giao lịch/RoomBlock; không lộ nội dung lịch xung đột |
| ROOM_REQUIREMENTS_NOT_MET | 409 | Phòng không còn đáp ứng sức chứa/thiết bị |
| ROOM_INACTIVE | 409 | Phòng không hoạt động |
| VERSION_CONFLICT | 409 | Dữ liệu đã thay đổi từ lần đọc |
| INVALID_STATE | 409 | Thao tác không hợp lệ với trạng thái/giờ hiện tại |
| REFERENCE_NO_LONGER_VALID | 409 | Người tổ chức/người dự/dự án/danh mục không còn hợp lệ |
| PENDING_CHANGE_EXISTS | 409 | Đã có đề nghị đang chờ |
| IMPACT_PLAN_INCOMPLETE | 409 | Thiếu phương án cho lịch bị ảnh hưởng |
| IMPACT_PLAN_STALE | 409 | Kế hoạch không khớp dữ liệu hiện tại |
| ACTIVE_DEPENDENCIES_EXIST | 409 | Còn lịch/người/dữ liệu ngăn ngừng danh mục |
| LAST_ADMIN_PROTECTED | 409 | Thao tác làm mất admin hoạt động cuối |
| DUPLICATE_CODE | 409 | Mã/email đã được sử dụng |
| IDEMPOTENCY_KEY_REUSED | 409 | Cùng key nhưng khác nội dung |
| EMPTY_AUDIENCE | 409 | Không có tài khoản hợp lệ nhận thông báo |
| RATE_LIMITED | 429 | Vượt hạn đăng nhập/thao tác được công bố |

Cảnh báo không chặn gửi yêu cầu: PREFERRED_ROOM_BUSY và NO_MATCHING_ROOM. Cảnh báo không được dùng thay lỗi ở bước duyệt; duyệt phải đáp ứng đầy đủ điều kiện.

```json
{
  "code": "VERSION_CONFLICT",
  "message": "Yêu cầu đã thay đổi. Vui lòng tải lại trước khi xử lý.",
  "fieldErrors": [],
  "requestId": "d1e247d4-9e4d-446c-b566-fe85d009b9e4"
}
```

### 23.5. API bổ sung và chuẩn truy cập

- POST /api/v1/auth/refresh, /auth/logout; POST /api/v1/me/change-password; POST /api/v1/users/{id}/reset-password.
- GET/POST /api/v1/departments, /projects, /equipment; GET/PATCH từng đối tượng theo id. Thay đổi trạng thái nằm trong PATCH có kiểm tra đầy đủ phụ thuộc.
- GET/POST /api/v1/users; GET/PATCH /users/{id}. Chỉ admin được tạo/sửa; nhân viên tra cứu danh bạ tối thiểu gồm id, tên, phòng ban và trạng thái để chọn người dự, không thấy thông tin phiên hoặc bảo mật.
- GET/POST /api/v1/projects/{id}/members và PATCH /projects/{id}/members/{userId}; admin quản lý. Nhân viên chỉ xem thành viên của dự án mình tham gia.
- GET /api/v1/announcements và /announcements/{id}: admin xem thông báo đã phát hành; người nhận truy cập qua thông báo cá nhân, không có quyền xem toàn bộ danh sách nhận.
- POST /api/v1/announcements/preview: xem số tài khoản ACTIVE theo phạm vi, không giữ danh sách nhận.
- GET /api/v1/room-blocks và /room-blocks/{id}: admin xem đầy đủ; nhân viên chỉ thấy khoảng chặn và thông tin vận hành được phép công khai trên lịch phòng.

## 24. Yêu cầu phi chức năng, vận hành và nghiệm thu bổ sung

### 24.1. Mục tiêu kiểm thử vận hành

Các số dưới đây là mục tiêu nghiệm thu do tài liệu đặt ra, không phải kết quả đã đo trên một hệ thống hiện có.

- Môi trường kiểm thử: ứng dụng và PostgreSQL trên máy tối thiểu 2 vCPU, 4 GB RAM; dữ liệu 200 tài khoản, 10 phòng, 20.000 booking, 100.000 thông báo cá nhân.
- Tải đọc 20 người dùng đồng thời trong 5 phút: p95 của API tìm phòng, lịch và danh sách thông báo không quá 1 giây, đo ở phía ứng dụng sau khởi động ổn định, không tính mạng Internet của người dùng.
- Thử tối thiểu 20 yêu cầu duyệt cạnh tranh cùng phòng/giờ: đúng một lịch được duyệt; mọi yêu cầu thất bại trả lỗi nghiệp vụ hoặc lỗi thử lại có kiểm soát, không tạo lịch trùng.
- Trong điều kiện dịch vụ xử lý nền hoạt động bình thường, ít nhất 99% sự kiện có thông báo trong 60 giây sau commit. Khi phục hồi sau lỗi, xử lý lại không mất sự kiện và không sinh bản trùng.
- Tác vụ hết hạn PENDING, tự hủy NO_SHOW và tự kết thúc phiên chạy tối đa cách nhau 60 giây; API luôn kiểm tra hạn hiện tại. Tìm trống/duyệt dùng giờ giải phóng hiệu lực kể cả khi tác vụ đang dừng; thao tác ghi phải chuẩn hóa trạng thái quá hạn cùng giao dịch.
- Client có thể làm mới hộp thông báo định kỳ tối thiểu cách 30 giây khi màn hình đang hoạt động; việc hiển thị tức thời không phải điều kiện duyệt/hủy thành công.

### 24.2. An toàn dữ liệu và phục hồi

- Triển khai thực tế dùng HTTPS; cấu hình kết nối, khóa ký hoặc bí mật phiên, tài khoản database lấy từ biến môi trường/cơ chế quản lý bí mật. Dữ liệu demo không được dùng làm thông tin truy cập thật.
- Kiểm tra quyền ở backend cho mọi endpoint và đối tượng; kiểm thử thay id để truy cập booking, thông báo và sự cố của người khác.
- Sao lưu database mỗi 24 giờ, giữ tối thiểu 30 bản ngày; mục tiêu mất dữ liệu tối đa 24 giờ và phục hồi trong 4 giờ đối với phạm vi triển khai này. Phải diễn tập khôi phục ít nhất một lần khi bàn giao.
- Lịch sử booking, quyết định, sự cố, khoảng chặn và audit giữ trong suốt thời gian sử dụng hệ thống, không có API xóa cứng. Chính sách lưu trữ dài hạn khác chỉ được áp dụng sau khi cập nhật yêu cầu và đánh giá ảnh hưởng.
- Notification/Announcement không xóa qua API; bản outbox đã hoàn tất có thể dọn sau 30 ngày nếu định danh chống trùng đã được lưu bền vững ở dữ liệu thông báo. Outbox chưa hoàn tất không được dọn.
- Sự kiện lỗi thử lại với khoảng cách tăng dần, tối đa 10 lần tự động trước khi đánh dấu cần xử lý. Lưu số lần, lần tiếp theo, lỗi gần nhất; có lệnh vận hành được bảo vệ để phát lại cùng eventId. Không phát sinh eventId mới khi thử lại.
- Migration không được âm thầm xóa dữ liệu hiện có. Việc nâng schema và dữ liệu mẫu phải tách biệt; dữ liệu mẫu chỉ nạp cho môi trường demo/test.

### 24.3. Theo dõi vận hành và tài liệu bàn giao

- Có health/readiness kiểm tra kết nối database; readiness không báo sẵn sàng khi migration chưa thành công.
- Theo dõi lỗi API, xung đột đặt phòng, số outbox tồn/chờ quá 60 giây và số sự kiện cần xử lý lại. Thông tin vận hành chi tiết chỉ dành cho admin vận hành, không công khai qua endpoint không xác thực.
- README phải có: biến môi trường, chạy Docker Compose, migration, dữ liệu demo, tài khoản kiểm thử, chạy kiểm thử, khôi phục backup, thử lại sự kiện lỗi và các quyết định nghiệp vụ trong phần 19.
- Có OpenAPI phản ánh đúng endpoint, field, enum, phân trang, lỗi và quyền; có ví dụ thực hiện từ gửi yêu cầu đến duyệt và xử lý sự cố.
- Có kiểm thử tích hợp cho giao dịch đồng thời trên PostgreSQL, không chỉ kiểm thử giả lập repository.

### 24.4. Tiêu chí nghiệm thu bổ sung

| ID | Tình huống | Kết quả bắt buộc |
|---|---|---|
| AC-33 | Đăng nhập bằng mật khẩu tạm | Chỉ được đổi mật khẩu/xem danh tính/đăng xuất cho tới khi đổi thành công |
| AC-34 | Khóa tài khoản hoặc đổi/reset mật khẩu | Phiên cũ không tiếp tục truy cập được |
| AC-35 | Vượt 5 lần đăng nhập sai trong 15 phút theo cặp định danh/nguồn | Trả 429, không tiết lộ email tồn tại |
| AC-36 | Hai admin đồng thời hạ quyền/khóa admin cuối | Luôn còn ít nhất một admin ACTIVE |
| AC-37 | Ngừng phòng ban còn nhân viên ACTIVE | Bị từ chối; chuyển nhân viên trước mới thực hiện được |
| AC-38 | Bổ nhiệm HEAD thứ hai hoặc cấp quyền qua vai trò dự án | Không có hai HEAD; vai trò dự án không cho quyền duyệt |
| AC-39 | Ngừng loại thiết bị hoặc giảm số lượng có phụ thuộc | Bị chặn đúng danh sách hồ sơ/lịch ảnh hưởng |
| AC-40 | Người dùng bị bỏ khỏi danh sách sau duyệt thay đổi | Nhận thông báo tối thiểu; không xem được nội dung mới |
| AC-41 | Thêm thành viên dự án sau khi thông báo đã phát | Không nhận ngược thông báo cũ |
| AC-42 | Đọc tất cả đồng thời có thông báo mới | Bản mới sau mốc đánh dấu vẫn chưa đọc |
| AC-43 | Thống kê qua ranh giới khoảng báo cáo | Tính phần giao theo đúng nguồn: lịch dự kiến, allocation, usage; không xóa sử dụng thực khi booking bị hủy |
| AC-44 | Kiểm thử tải và xử lý thông báo | Đạt các mục tiêu đo tại 24.1; báo cáo kết quả thực đo |
| AC-45 | Sao lưu và diễn tập khôi phục | Phục hồi dữ liệu/giao dịch, không mất liên kết lịch sử, đạt mục tiêu thời gian |
| AC-46 | Chạy Docker Compose từ dữ liệu mới và khởi động lại dữ liệu cũ | Dịch vụ sẵn sàng, migration đúng, không tự xóa/ghi đè dữ liệu nghiệp vụ |

### 24.5. Hồ sơ nghiệm thu

Bàn giao repository, lịch sử commit, spec này, OpenAPI, migration, cấu hình Docker Compose, README, dữ liệu demo, kết quả 76 tiêu chí nghiệm thu, báo cáo tải/đồng thời và biên bản diễn tập phục hồi. Các điểm không đạt phải được liệt kê rõ; không đánh dấu hoàn thành chỉ vì ứng dụng khởi động được.

## 25. Check-in/check-out và xác nhận sử dụng cuộc họp

### 25.1. Mục tiêu và mức xác minh

- Check-in xác nhận một cuộc họp đã bắt đầu sử dụng phòng bằng thao tác của tài khoản có quyền; check-out xác nhận kết thúc và trả phòng.
- Đây là xác nhận có danh tính và dấu thời gian backend, không phải cảm biến xác minh hiện diện vật lý. Không suy ra tất cả người được mời đã tới họp; không thực hiện RSVP hoặc điểm danh cá nhân.
- Người tổ chức hoặc một người tham dự nội bộ ACTIVE có thể check-in thay mặt cuộc họp. Một lần check-in mở một phiên sử dụng chung, không mở phiên riêng cho từng người.
- Admin được xác nhận vận hành trong cùng cửa sổ thời gian, phải ghi lý do; lưu evidenceType=ADMIN_CONFIRMATION, không giả mạo thành thao tác của người tổ chức. Thao tác thông thường lưu evidenceType=USER_CONFIRMATION.
- Không check-in bằng tài khoản khách ngoài, không nhận checkedInAt/checkedOutAt do client cung cấp. Không sửa hồi tố để che việc không đến.
- Mức ân hạn 10 phút là quy tắc do bản phân tích lựa chọn, chưa phải một chính sách do người dùng chỉ định. Giá trị được chốt cho hồ sơ khi duyệt để thay cấu hình sau này không âm thầm đổi hạn đã thông báo.

### 25.2. Tách trạng thái phê duyệt và trạng thái sử dụng

Booking.status giữ PENDING/APPROVED/REJECTED/CANCELLED/EXPIRED. BookingUsage.status có:

| Trạng thái sử dụng | Điều kiện | Kết quả |
|---|---|---|
| NOT_APPLICABLE | Booking chưa được duyệt hoặc bị hủy trước khi sử dụng | Không có phiên sử dụng |
| NOT_CHECKED_IN | Đã duyệt nhưng chưa check-in | Giữ phòng cho đến hạn check-in hoặc quyết định khác |
| IN_USE | Check-in hợp lệ | Có đúng một UsageSegment đang mở |
| COMPLETED | Check-out hoặc đã hết giờ sau khi check-in | Phiên đã đóng, không check-in lại |
| NO_SHOW | Đến hạn mà chưa check-in | Booking CANCELLED, lý do NO_SHOW |
| INTERRUPTED | Admin hủy vì vận hành khi đang IN_USE | Đóng đoạn sử dụng, giữ lịch sử |

- Khi duyệt lần đầu: APPROVED + NOT_CHECKED_IN, tạo allocation và checkInDeadline.
- Duyệt thay đổi trước giờ bắt đầu: cập nhật giờ/phòng/allocation và tính lại hạn theo giờ mới, giữ NOT_CHECKED_IN.
- Hủy trước check-in bởi người dùng/admin: CANCELLED + NOT_APPLICABLE, lý do USER_CANCELLED/ADMIN_CANCELLED; không tính NO_SHOW.
- Hủy trong IN_USE bởi admin: CANCELLED + INTERRUPTED; không cho biến thành NO_SHOW.
- Check-out đúng/sớm hoặc tự kết thúc: APPROVED + COMPLETED; giữ quyết định duyệt để truy vết, không coi đây là một lần hủy.
- Khi có check-in, không còn được đổi lịch theo BookingChange, kể cả trường hợp phát sinh trước giờ bắt đầu do sai lệch trạng thái; mọi đề nghị phải thỏa đồng thời điều kiện thời gian và NOT_CHECKED_IN.

### 25.3. Cửa sổ check-in và tự hủy

```text
checkInOpenAt = startAt
checkInDeadline = min(startAt + 10 phút, endAt)
Check-in hợp lệ khi startAt <= serverNow < checkInDeadline
```

- Không check-in trước giờ bắt đầu; không mở thêm quyền sử dụng trước khoảng đã được duyệt.
- Tại đúng deadline, check-in không còn hợp lệ. Nếu thời lượng cuộc họp dưới 10 phút thì cửa sổ kết thúc tại endAt, không kéo dài sang lịch tiếp theo.
- Thời gian xét là giờ backend khi giữ quyền xử lý độc quyền hồ sơ; không dùng giờ thiết bị hoặc giờ bắt đầu gửi request.
- Backend kiểm tra booking APPROVED, usage NOT_CHECKED_IN, quyền, trạng thái tài khoản và allocation/phòng còn hợp lệ.
- Check-in thành công: ghi checkedInAt/By, evidenceType, tăng phiên bản, mở UsageSegment tại phòng hiện tại và phát thông báo.
- Khi serverNow >= deadline và chưa có check-in được chấp nhận: tự chuyển CANCELLED + NO_SHOW, ghi cancellationReasonCode=NO_SHOW, effectiveCancelledAt=deadline, processedAt=giờ thực thi, giải phóng tại deadline, đóng đề nghị chờ nếu còn và tạo một sự kiện thông báo.
- Admin cũng không check-in sau hạn để phục hồi lịch đã NO_SHOW. Muốn sử dụng tiếp phải tạo và duyệt một yêu cầu mới với thời gian bắt đầu trong tương lai và kiểm tra phòng lại.
- Check-in gửi lại sau khi đã thành công trả kết quả hiện có, không đổi checkedInAt hoặc tạo thêm segment. Nếu phiên đã kết thúc, chỉ trả biên nhận cũ khi là retry cùng Idempotency-Key; thao tác mới bị từ chối.

Ví dụ: họp 09:00–10:00, chưa check-in lúc 09:10 thì tự hủy và giải phóng 09:10–10:00. Check-in lúc 09:09 được chấp nhận; đúng 09:10 bị từ chối. Họp 09:00–09:05 thì hạn là 09:05, không phải 09:10.

### 25.4. Check-out, kết thúc tự động và giải phóng sớm

- Chủ lịch hoặc người đã check-in được check-out; admin được can thiệp với lý do. Người chỉ có trong danh sách mời nhưng không phải người check-in/chủ lịch không được kết thúc cuộc họp.
- Chỉ check-out khi booking APPROVED, usage IN_USE và checkedInAt < serverNow < endAt. Không nhận khoảng sử dụng bằng 0; thao tác ngay cùng timestamp mở phiên phải thử lại sau.
- Check-out ghi checkedOutAt/By, endSource=USER_CHECKOUT hoặc ADMIN_CHECKOUT, đóng segment, chuyển COMPLETED và giải phóng allocation tại thời điểm đó trong cùng giao dịch.
- Giữ nguyên startAt/endAt dự kiến của booking; không đổi endAt thành giờ trả phòng vì cần so sánh kế hoạch và thực tế.
- Sau check-out không được mở lại phiên dù chưa tới endAt. Phần thời gian đã trả có thể được cấp cho yêu cầu mới, vẫn tuân thủ startAt trong tương lai và quy trình duyệt.
- Nếu đã check-in nhưng không check-out trước endAt: hệ thống đóng segment tại endAt, usage COMPLETED, endSource=SYSTEM_SCHEDULED_END. checkedOutAt giữ null; effectiveEndedAt=endAt là mốc suy định, không giả lập thao tác người dùng.
- Tại hoặc sau endAt, yêu cầu check-out mới trả kết quả đã tự kết thúc với cảnh báo AUTO_ENDED; không ghi endSource thành USER_CHECKOUT. Retry không tạo sự kiện kết thúc thứ hai.
- Không cho tiếp tục chiếm phòng quá giờ dự kiến; muốn kéo dài phải có booking mới được duyệt cho khung tiếp theo. Hệ thống không tuyên bố đã cưỡng chế người ở trong phòng rời đi.
- Đến giờ bắt đầu mà chưa check-in: gửi một thông báo nhắc check-in nếu chưa quá hạn. Nếu tác vụ đến muộn sau deadline thì chỉ gửi kết quả NO_SHOW, không gửi nhắc lỗi thời.

### 25.5. Khoảng chiếm phòng và chuyển phòng trong khi sử dụng

RoomAllocation là nguồn kiểm tra trống. Mỗi lần bố trí có roomId, fromAt, untilAt dự kiến và releasedAt tùy chọn. Khoảng giữ hiệu lực là (nếu mốc kết thúc <= fromAt thì khoảng rỗng):

```text
[fromAt, min(untilAt, releasedAt nếu có))
```

- Khi duyệt: allocation=[startAt,endAt), chưa có releasedAt. Giờ check-in muộn không làm mất quyền giữ phòng trong khoảng chờ hợp lệ.
- Check-out, hủy hoặc chuyển đi: ghi releasedAt, không xóa allocation cũ. Trả phòng trước fromAt tạo khoảng rỗng; không có khoảng giữ âm.
- Tự hủy NO_SHOW: releasedAt=checkInDeadline kể cả khi xử lý nền chậm. Chỉ áp dụng cắt theo deadline khi giờ hiện tại đã tới deadline và vẫn chưa check-in; trước đó vẫn giữ đủ tới endAt vì người dùng còn có thể check-in hợp lệ. Tìm trống phải tính hạn hiệu lực này; trước khi ghi một lịch mới, backend hoàn tất chuyển NO_SHOW của hồ sơ cũ trong cùng cơ chế bảo vệ phòng.
- Giải phóng không sửa lại lịch sử đã chiếm trước mốc trả; xung đột chỉ dựa trên các khoảng hiệu lực sau khi cắt.
- Duyệt thay đổi trước giờ họp: giải phóng allocation cũ và tạo allocation mới nguyên tử. Hủy/đổi phòng sớm không được làm phòng bận trở lại do retry tác vụ cũ.
- Chuyển phòng khi NOT_CHECKED_IN: giữ nguyên deadline; allocation mới bắt đầu tại startAt nếu chưa tới giờ, hoặc tại giờ chuyển nếu đã bắt đầu. Allocation cũ được giải phóng tại giờ chuyển.
- Chuyển phòng khi IN_USE: tại thời điểm T, đóng UsageSegment phòng cũ với endSource=ADMIN_TRANSFER, cắt allocation cũ tại T, tạo allocation phòng mới [T,endAt) và mở segment mới tại T. Phiên cuộc họp vẫn IN_USE; không yêu cầu check-in lại, không cấp lại 10 phút.
- Dấu ADMIN_TRANSFER ghi nhận quyết định vận hành; không chứng minh người tham dự đã di chuyển vật lý ngay tại T. Thông báo phải nêu phòng mới và người xử lý.
- Mọi đoạn sử dụng thuộc một allocation, không vượt endAt; một booking có tối đa một segment đang mở. Các segment không được giao nhau, không cộng trùng thời gian tại điểm chuyển.
- RoomBlock, giảm thiết bị/ngừng phòng và báo cáo thống kê đều dựa trên allocation còn hiệu lực, không coi lịch đã trả sớm vẫn giữ toàn bộ thời gian dự kiến.
- Khi admin hủy IN_USE vì sự cố, endSource=ADMIN_CANCELLATION, kết quả INTERRUPTED; đoạn đã dùng vẫn được báo cáo, phần sau hủy được giải phóng.

### 25.6. Đồng thời, tác vụ nền và bằng chứng

- Check-in, check-out, tự hủy, tự kết thúc, chuyển/hủy lịch và duyệt lịch mới phải dùng cùng cơ chế bảo vệ hồ sơ/allocation/phòng, thứ tự tài nguyên nhất quán.
- Check-in và NO_SHOW cạnh tranh: chỉ một kết quả hợp lệ; khi tác vụ đã xử lý đúng hạn thì request check-in không được khôi phục lại.
- Check-out và tự kết thúc cạnh tranh: một kết quả cuối; mốc backend xét trước endAt cho phép check-out, tại/sau endAt phải kết thúc theo giờ.
- Mọi request cập nhật gửi expectedVersion; response xung đột cho phép client tải lại. Retry thành công cùng key trả biên nhận đã lưu trước khi áp lại kiểm tra trạng thái hiện tại.
- Tác vụ nền chạy lại cùng eventId không tạo thêm audit nghiệp vụ, thông báo, allocation hoặc segment. Tác vụ nhận phiên bản cũ phải đọc lại và bỏ qua nếu điều kiện đã không còn đúng.
- Dừng tác vụ không cho phép check-in quá hạn hoặc chiếm quá giờ: API đọc trả trạng thái hiệu lực; thao tác ghi chuẩn hóa các trạng thái quá hạn dưới khóa trước khi quyết định.
- Bằng chứng gồm userId, vai trò/quyền tại lúc thao tác, nguồn xác nhận, giờ backend, requestId, bookingVersion và phòng/allocation. SYSTEM_NO_SHOW và SYSTEM_SCHEDULED_END có actor=SYSTEM, không gán cho admin/người tổ chức.
- Nếu có khiếu nại về NO_SHOW hoặc giờ sử dụng: admin được ghi biên bản bổ sung có lý do và tham chiếu bằng chứng; không sửa xóa dấu thời gian gốc, khôi phục quyền giữ phòng cũ hoặc âm thầm cộng thời gian vào chỉ số đã xác nhận. Mọi hiệu chỉnh báo cáo phải được thể hiện là điều chỉnh riêng có audit.

### 25.7. API bổ sung

| Endpoint | Request | Response |
|---|---|---|
| GET /api/v1/bookings/{id}/usage | Không | Quyền hành động hiện tại, deadline, usage, các segment theo quyền xem booking |
| POST /api/v1/bookings/{id}/check-in | expectedVersion; reason bắt buộc nếu admin xác nhận vận hành | Booking/usage phiên bản mới, checkedInAt/By, evidenceType |
| POST /api/v1/bookings/{id}/check-out | expectedVersion; reason bắt buộc nếu admin can thiệp | Kết quả kết thúc, giờ hiệu lực, nguồn xác nhận, releasedAt |
| POST /api/v1/bookings/{id}/usage-notes | expectedVersion, reason, nội dung/bằng chứng tham chiếu | Biên bản admin bổ sung, không sửa dấu thời gian gốc; chỉ thông tin vận hành, không thêm nội dung HR riêng |

Check-in/check-out hỗ trợ Idempotency-Key. Không cung cấp endpoint công khai để ép NO_SHOW, tự check-in hàng loạt hoặc truyền timestamp tùy ý. GET usage trả canCheckIn/canCheckOut dựa trên quyền và giờ backend nhưng thao tác ghi luôn kiểm tra lại.

Mã lỗi bổ sung: CHECK_IN_TOO_EARLY (409), CHECK_IN_DEADLINE_PASSED (409), USAGE_NOT_ACTIVE (409), USAGE_ALREADY_FINISHED (409), CHECK_OUT_FORBIDDEN (403). Lỗi deadline phải trả trạng thái cuối hiện hành, không chỉ thông báo lỗi trong khi dữ liệu vẫn có thể được duyệt lại.

### 25.8. Ca sử dụng UC-06: xác nhận và trả phòng

**Tiền điều kiện:** booking APPROVED, người thao tác đủ quyền, phòng/allocation hợp lệ.

**Luồng chính:** mở lịch → backend xác định cửa sổ check-in → người dùng check-in → hiển thị IN_USE và người xác nhận → kết thúc họp, người có quyền check-out → ghi thời gian/nguồn xác nhận → giải phóng phần còn lại → cập nhật lịch và thông báo.

**Ngoại lệ:** chưa đến giờ thì từ chối; quá deadline thì NO_SHOW; hết endAt thì đóng theo giờ và ghi rõ suy định; lỗi/mạng làm gửi lặp không mở thêm phiên. Chuyển phòng/sự cố thực hiện theo 25.5, không xóa dấu sử dụng cũ.

**Hậu điều kiện:** phân biệt được không đến, có xác nhận sử dụng, có xác nhận trả phòng và kết thúc theo giờ; phòng không bị chiếm quá allocation hiệu lực.

## 26. Thống kê thời gian sử dụng và tiêu chí nghiệm thu mở rộng

### 26.1. Nguồn và công thức

Mọi chỉ số thời lượng trong khoảng báo cáo [A,B) cộng phần giao của các đoạn tương ứng với [A,B), theo phòng ban tổ chức đã chốt, team, dự án và loại cuộc họp theo hồ sơ/audit tại thời điểm sử dụng; giới hạn riêng tư theo phần 27.

| Chỉ số | Nguồn/cách tính |
|---|---|
| Giờ theo lịch được bố trí | Khoảng dự kiến của booking APPROVED, gồm lịch COMPLETED; trình bày riêng số giờ đã duyệt rồi bị hủy trong audit |
| Giờ giữ phòng hiệu lực | Tổng độ dài các RoomAllocation sau cắt releasedAt, kể cả phần đã giữ trước một lần hủy; không đồng nghĩa giờ họp thực tế |
| Giờ sử dụng có xác nhận kết thúc | Tổng UsageSegment kết thúc bằng USER_CHECKOUT, ADMIN_CHECKOUT, ADMIN_TRANSFER hoặc ADMIN_CANCELLATION; ghi rõ nguồn vận hành khi không do người dự xác nhận |
| Giờ sử dụng kết thúc theo suy định | Segment có check-in nhưng kết thúc bằng SYSTEM_SCHEDULED_END; tách riêng, không gộp ngầm vào giờ có xác nhận kết thúc |
| Số NO_SHOW | Số booking CANCELLED có cancellationReasonCode=NO_SHOW |
| Tỷ lệ NO_SHOW | Tập mẫu gồm booking từng được duyệt có checkInDeadline trong khoảng báo cáo và <= giờ chốt, không bị hủy thủ công trước check-in. Tử số là số NO_SHOW trong tập; mẫu số là toàn bộ tập, gồm IN_USE/COMPLETED/INTERRUPTED/NO_SHOW; mẫu số 0 trả null |
| Giờ giải phóng sớm | Phần còn lại tới endAt được trả bởi check-out sớm, NO_SHOW hoặc hủy; chia nhóm lý do, không cộng trùng khoảng sau nhiều lần chuyển phòng |
| Đang sử dụng | Số usage IN_USE có segment còn mở trước endAt tại thời điểm truy vấn; không suy ra từ giờ dự kiến |

- Thời gian chờ check-in trước checkedInAt là giữ phòng, không tính thời gian sử dụng.
- Booking NO_SHOW không có UsageSegment và có thời gian sử dụng bằng 0, dù đã giữ phòng trong thời gian ân hạn.
- Booking bị admin hủy sau check-in vẫn giữ số phút đã sử dụng và kết quả INTERRUPTED.
- Thống kê theo phòng dùng segment/allocation của từng phòng khi chuyển; không quy toàn bộ cuộc họp cho phòng cuối cùng.
- Không dùng attendeeCount làm số người thực tế đã có mặt. Hệ thống không có chỉ số tỷ lệ tham dự cá nhân.
- Biên bản hiệu chỉnh hiển thị riêng, không làm mất số liệu nguồn. Tổng hợp phải có ngày giờ chốt và bộ lọc trạng thái/nguồn xác nhận.
- Phân trang/bộ lọc/giới hạn 366 ngày ở phần 23 áp dụng cho GET /api/v1/reports/usage; chỉ ADMIN có quyền xem toàn công ty.

### 26.2. Tiêu chí nghiệm thu bổ sung

| ID | Tình huống | Kết quả bắt buộc |
|---|---|---|
| AC-47 | Check-in trước startAt | Từ chối, không mở segment hoặc chiếm thêm thời gian |
| AC-48 | Chủ lịch/người tham dự ACTIVE check-in trong cửa sổ | IN_USE, đúng phòng, giờ backend và danh tính người xác nhận |
| AC-49 | Người ngoài lịch hoặc tài khoản INACTIVE check-in | Từ chối, admin can thiệp phải có lý do riêng |
| AC-50 | Không check-in tới deadline | CANCELLED + NO_SHOW, release tại deadline, thông báo một lần |
| AC-51 | Check-in đúng deadline hoặc sau đó | Từ chối; không hồi sinh lịch kể cả admin |
| AC-52 | Cuộc họp 5 phút chưa check-in | Deadline=endAt, không giữ phòng thêm tới phút thứ 10 |
| AC-53 | Check-in đua với tác vụ NO_SHOW | Chỉ một kết quả hợp lệ, không vừa NO_SHOW vừa có segment mở |
| AC-54 | Retry check-in cùng key hoặc lặp trước khi kết thúc | Không đổi giờ check-in, không tạo segment/thông báo trùng |
| AC-55 | Chủ lịch/người check-in check-out sớm | COMPLETED, cắt allocation ngay, phòng trống phần còn lại |
| AC-56 | Người dự khác cố check-out hoặc check-out trước check-in | Bị từ chối đúng quyền/trạng thái |
| AC-57 | IN_USE tới endAt không check-out | COMPLETED, SYSTEM_SCHEDULED_END, checkedOutAt=null, báo cáo suy định |
| AC-58 | Check-out tới muộn hoặc đua tác vụ kết thúc | Chỉ một sự kiện kết thúc, không ghi giả xác nhận người dùng |
| AC-59 | Check-out xong check-in lại trong giờ dự kiến | Bị từ chối; lịch mới cần yêu cầu/duyệt mới |
| AC-60 | Tác vụ nền dừng qua deadline/endAt | Tìm trống/duyệt không giữ phòng quá hạn; chuẩn hóa nguyên tử trước ghi |
| AC-61 | Chuyển phòng trong IN_USE | Hai segment liền kề đúng phòng, không đếm trùng, chỉ giữ phòng mới từ giờ chuyển |
| AC-62 | Admin hủy IN_USE vì sự cố | INTERRUPTED, giữ thời gian đã dùng, giải phóng còn lại, có audit/thông báo |
| AC-63 | Admin thêm biên bản khiếu nại NO_SHOW | Không sửa dấu gốc hoặc chiếm lại phòng; hiệu chỉnh có nguồn riêng |
| AC-64 | Họp 09:00–10:00, check-in 09:05, check-out 09:40 | Giữ phòng 40 phút, sử dụng xác nhận 35 phút, trả sớm 20 phút |
| AC-65 | Cùng lịch nhưng không check-out | Sử dụng suy định 55 phút, không trình bày như thời gian có xác nhận kết thúc |
| AC-66 | Thống kê NO_SHOW, hủy sau check-in và chuyển phòng | NO_SHOW sử dụng 0; hủy giữ đoạn đã dùng; chuyển phòng chia đúng từng phòng |

### 26.3. Ranh giới chức năng đã thống nhất

Các nhóm người dùng đã xác định không cần, tiếp tục nằm ngoài phạm vi:

1. Đăng ký tài khoản công khai, tài khoản cho khách ngoài và RSVP của người tham dự.
2. Nhiều công ty và nhiều múi giờ nghiệp vụ.
3. Đặt lặp, duyệt nhiều cấp, ủy quyền duyệt và đặt hộ.
4. Quyền duyệt riêng cho trưởng phòng/PM, hạn mức hoặc thứ hạng ưu tiên theo chức danh.

Check-in/check-out là xác nhận sử dụng chung cuộc họp, không làm phát sinh RSVP, đăng ký khách ngoài hoặc quyền duyệt theo chức danh. Những ranh giới tích hợp khác ở phần 2.2 vẫn được giữ nguyên.

## 27. Team, họp liên phòng ban và mục đích cuộc họp

### 27.1. Mô hình phân loại độc lập

Một cuộc họp có các chiều thông tin riêng, không dùng projectId làm trường đại diện cho mọi nhóm làm việc:

| Chiều thông tin | Ý nghĩa | Bắt buộc |
|---|---|---|
| Người tổ chức | Người tạo, chịu trách nhiệm hồ sơ | Có, lấy từ tài khoản |
| Phòng ban chủ trì | Đơn vị của người tổ chức lúc gửi | Có, backend ghi nhận |
| Team tổ chức | Nhóm làm việc chủ trì nếu có | Không, tối đa một team |
| Dự án liên quan | Ngữ cảnh dự án nếu có | Không, tối đa một dự án |
| Phòng ban phối hợp | Các đơn vị có người tham dự hoặc được chọn phối hợp | Không giới hạn một đơn vị |
| Loại cuộc họp | Mục đích nghiệp vụ của cuộc họp | Có |
| Người tham dự | Danh sách nhân viên được mời cụ thể | Có người tổ chức; thêm người khác tùy nhu cầu |
| Mức riêng tư | Chính sách xem nội dung | Có giá trị hiệu lực, backend xác định mặc định |

- Team và dự án độc lập: có thể chọn cả hai, một trong hai hoặc không chọn gì. Không tạo dự án giả để đặt họp cho một team hoặc phỏng vấn.
- Không bắt buộc cùng phòng ban giữa người tổ chức, team và người tham dự. Team không bắt buộc thuộc một dự án hoặc phòng ban duy nhất.
- Team/dự án/phòng ban chỉ phân loại và hỗ trợ lọc, thống kê, thông báo đúng phạm vi; không tự thêm toàn bộ thành viên vào danh sách mời hoặc tự cấp quyền xem nội dung.
- Việc chọn team không đồng nghĩa mọi người dự phải là thành viên team; họp team có thể mời người bên ngoài team. Người tổ chức phải là thành viên ACTIVE của team được chọn.
- Một nhân viên có thể thuộc nhiều team đồng thời, với vai trò khác nhau trong mỗi team.

### 27.2. Quản lý team

- Team có code duy nhất 1–50 ký tự, name 1–150 ký tự, description tối đa 2.000 ký tự và status ACTIVE/ARCHIVED.
- Admin tạo/sửa/lưu trữ team và quản lý thành viên. TeamMember gồm teamId, userId, teamRole LEAD/MEMBER, status ACTIVE/INACTIVE; mỗi cặp team–người chỉ có một tư cách hiện hành và có lịch sử thay đổi.
- Chỉ nhân viên ACTIVE được thêm/kích hoạt; chỉ team ACTIVE nhận thành viên mới hoặc được chọn cho yêu cầu mới. Một team có tối đa một LEAD ACTIVE, có thể chưa bổ nhiệm LEAD.
- LEAD không có quyền duyệt, sửa hoặc hủy lịch của thành viên. Muốn duyệt phải có quyền hệ thống ADMIN và vẫn không tự duyệt.
- Nhân viên tra cứu danh mục team ACTIVE ở mức id/code/name; chỉ xem danh sách thành viên của team mình tham gia. Admin quản lý toàn bộ danh sách.
- ARCHIVED giữ nguyên lịch sử, không tự hủy booking đã duyệt. PENDING/BookingChange có gắn team phải kiểm tra lại trạng thái team và tư cách người tổ chức lúc duyệt; nếu không hợp lệ thì trả REFERENCE_NO_LONGER_VALID để người tổ chức sửa/gửi lại.
- Team có thể được admin kích hoạt lại; không tự kích hoạt lại các thành viên đã rời team.
- Đổi tên, lưu trữ team hoặc người tổ chức rời team không làm thay đổi nhóm thống kê của dữ liệu lịch sử đã chốt.

### 27.3. Họp một hoặc nhiều phòng ban

- organizerDepartmentId thể hiện đơn vị chủ trì, không được thay bằng danh sách phòng ban phối hợp.
- relatedDepartmentIds khi gửi được hợp nhất, loại trùng với đơn vị chủ trì và phòng ban hiện tại của các nhân viên được chọn tham dự. Phần người dùng tự chọn phải là phòng ban ACTIVE; backend không tin vào phòng ban do client gán cho một người.
- Lưu tập đơn vị liên quan theo phiên bản hồ sơ tại lúc gửi/sửa/duyệt thay đổi. Người dự chuyển phòng ban sau đó không tự đổi phân loại lịch cũ.
- Ví dụ cuộc họp HR và kỹ thuật có thể do HR chủ trì, liên quan cả HR/IT, không có team hoặc dự án.
- Nếu báo cáo nhóm theo phòng ban chủ trì, mỗi booking chỉ thuộc một đơn vị. Nếu nhóm theo đơn vị phối hợp, một booking có thể xuất hiện ở nhiều nhóm: phải ghi rõ đây là số liên quan, không cộng các nhóm để thành tổng toàn công ty.
- Một người thuộc phòng ban có tên trong relatedDepartmentIds không tự trở thành người tham dự, không tự nhận thông báo cuộc họp và không tự có quyền xem chi tiết.
- Phòng ban liên quan được lưu trữ trước lúc duyệt làm yêu cầu cần được sửa loại bỏ/thay đơn vị đó; lịch đã duyệt vẫn giữ lịch sử.

### 27.4. Danh mục loại cuộc họp

meetingType là enum độc lập với team/dự án/phòng ban. Danh mục của hợp đồng này gồm:

| Mã | Mục đích | Mức riêng tư |
|---|---|---|
| GENERAL | Họp thông thường | STANDARD mặc định, được chọn CONFIDENTIAL |
| SYNC | Đồng bộ công việc team/bộ phận | STANDARD mặc định, được chọn CONFIDENTIAL |
| PLANNING | Lập kế hoạch, phân công | STANDARD mặc định, được chọn CONFIDENTIAL |
| REVIEW | Review tiến độ, sản phẩm hoặc kết quả công việc chung | STANDARD mặc định, được chọn CONFIDENTIAL |
| SALARY_REVIEW | Trao đổi/review lương | Bắt buộc CONFIDENTIAL |
| PERFORMANCE_REVIEW | Đánh giá cá nhân, trao đổi nhân sự | Bắt buộc CONFIDENTIAL |
| INTERVIEW | Phỏng vấn ứng viên | Bắt buộc CONFIDENTIAL |
| TRAINING | Đào tạo, chia sẻ nội bộ | STANDARD mặc định, được chọn CONFIDENTIAL |
| OTHER | Mục đích khác, diễn giải ngắn trong purpose | STANDARD mặc định, được chọn CONFIDENTIAL |

- SYNC chỉ mô tả mục đích, không tạo đặt lặp. Mỗi lần họp vẫn là một yêu cầu độc lập.
- Không giới hạn quyền tạo SALARY_REVIEW/INTERVIEW theo chức danh trong hệ thống; kiểm soát quy trình nhân sự bên ngoài không làm phát sinh vai trò duyệt HR/PM mới.
- Hồ sơ vẫn bắt buộc có purpose; chỉ cần mô tả mục đích đặt phòng, không yêu cầu ghi số tiền lương, nhận xét nhân sự, CV hoặc thông tin đánh giá ứng viên. Hệ thống quản lý phòng không lưu hồ sơ tuyển dụng/lương thay cho hệ thống HR.
- Đối với phỏng vấn, danh sách người dự nội bộ có thể gồm HR và người phỏng vấn; ứng viên bên ngoài chỉ tính vào attendeeCount. Không tạo tài khoản khách, không phát thông báo cho ứng viên và không có RSVP.
- Khi đổi loại từ thông thường sang loại nhạy cảm, nâng visibility ngay trong hồ sơ/đề nghị và mọi biểu diễn của nó. Đề nghị nhạy cảm không được hiển thị nội dung cho admin không phải người dự ngay cả khi lịch gốc đang STANDARD.
- Hồ sơ đã CONFIDENTIAL không được hạ về STANDARD kể cả khi đổi loại, nhằm bảo vệ lịch sử và thông báo cũ. Nếu client cố yêu cầu STANDARD cho loại nhạy cảm, trả 400 VISIBILITY_NOT_ALLOWED; nếu không gửi visibility thì backend tự chọn CONFIDENTIAL.

### 27.5. Quyền riêng tư xuyên suốt

| Người xem | STANDARD | CONFIDENTIAL |
|---|---|---|
| Người tổ chức/người tham dự hiện tại | Xem đầy đủ thông tin cuộc họp được phép | Xem đầy đủ thông tin cuộc họp được phép |
| Admin không phải người dự | Xem chi tiết để xử lý | Chỉ dữ liệu bố trí/vận hành đã che trường |
| Nhân viên không liên quan | Lịch bận/trống tối thiểu theo mục 7 | Chỉ phòng, khoảng bận, nhãn Cuộc họp riêng tư; không có tên người tổ chức hoặc mã hồ sơ liên kết chi tiết |
| Thành viên cùng team/dự án/phòng ban nhưng không được mời | Không tự có quyền xem chi tiết | Không có quyền xem chi tiết |

Dữ liệu vận hành admin được xem với CONFIDENTIAL: bookingId, người tổ chức và đơn vị chủ trì để liên hệ, thời gian, tổng số người, thiết bị cần, phòng mong muốn/phòng được bố trí, trạng thái quyết định/sử dụng, phiên bản, các hạn check-in và tác nhân xác nhận vận hành. Không trả title/purpose/note riêng, meetingType nhạy cảm, projectId/teamId, danh sách đơn vị phối hợp hoặc danh sách người tham dự nếu admin không thuộc cuộc họp. Trường phân loại hiển thị cho admin là CONFIDENTIAL.

- Admin vẫn được phân phòng/duyệt/từ chối/chuyển/hủy trên dữ liệu vận hành. Lý do quản trị chỉ mô tả vấn đề bố trí/phòng, không yêu cầu tiết lộ nội dung HR.
- Bảo vệ ở backend tại response DTO, truy vấn tìm kiếm, bộ lọc, thông báo, audit/history, đề nghị thay đổi và báo cáo; không chỉ ẩn cột ở giao diện.
- Admin không phải người dự không lọc/tìm CONFIDENTIAL theo tiêu đề, mục đích, loại nhạy cảm, team, dự án hoặc đơn vị phối hợp để suy ra dữ liệu bị che. Các bản ghi này được nhóm thành CONFIDENTIAL; có thể lọc theo thời gian/phòng/người tổ chức/đơn vị chủ trì/trạng thái vận hành.
- Thông báo cho chủ lịch/người dự có thể có nội dung cuộc họp. Thông báo cho admin vận hành hoặc toàn nhóm chỉ có thông tin vận hành và nhãn riêng tư. Không gửi thông báo về phỏng vấn/review lương cho toàn team/phòng ban chỉ vì liên quan về tổ chức.
- Người bị loại khỏi danh sách chỉ được nhận thông báo tối thiểu rằng họ không còn tham dự; không được đọc bản cập nhật hoặc lịch sử nhạy cảm. Liên kết thông báo luôn kiểm tra quyền hiện tại.
- Audit giữ dữ liệu gốc phục vụ truy vết có kiểm soát, nhưng endpoint history áp cùng quy tắc che trường; quyền ADMIN ứng dụng không cho phép bỏ qua chính sách nội dung riêng.
- Báo cáo toàn công ty theo loại/team/dự án/phòng ban phối hợp gộp các cuộc họp CONFIDENTIAL vào một nhóm riêng, không phân tách loại HR nhạy cảm hay nhóm bị che. Tổng thời gian giữ/sử dụng theo phòng và thống kê NO_SHOW vẫn có thể tính trên dữ liệu vận hành. Không có báo cáo toàn công ty cho USER chỉ vì họ tham dự một số cuộc họp.
- Các dữ liệu đã từng được chia sẻ khi STANDARD không thể thu hồi khỏi trí nhớ người xem; nâng mức riêng tư ngăn các lần đọc/phát tiếp theo, không tuyên bố xóa được bản sao đã được người khác lưu trước đó.

### 27.6. API, dữ liệu và bộ lọc bổ sung

- GET/POST /api/v1/teams; GET/PATCH /api/v1/teams/{id}; GET/POST /api/v1/teams/{id}/members; PATCH /api/v1/teams/{id}/members/{userId}. Quyền quản lý thuộc ADMIN; quyền tra cứu theo 27.2.
- GET /api/v1/meeting-types trả danh sách mã/tên và mức riêng tư tối thiểu. Đây là danh mục cố định của hợp đồng; không có API tạo loại tùy ý bỏ qua yêu cầu riêng tư.
- GET /api/v1/me/teams trả các team hiện tại của người dùng. Booking có organizingTeamId tùy chọn, relatedDepartmentIds, meetingType và visibility bên cạnh projectId.
- Request BookingChange là ảnh chụp đầy đủ, bao gồm các trường mới; response gửi cho người xử lý phải áp dụng chính sách riêng tư chặt hơn của lịch gốc và đề nghị.
- Bộ lọc booking/report bổ sung organizingTeamId, relatedDepartmentId, meetingType và visibility; chỉ áp dụng với các hồ sơ/trường người gọi được phép biết. Không làm tăng quyền xem do chọn một bộ lọc.
- Announcement audience loại TEAMS chốt thành viên ACTIVE tại thời điểm phát hành như PROJECTS. Quyền phát tin chung không cho quyền công khai một booking CONFIDENTIAL.
- Mã lỗi bổ sung: TEAM_MEMBERSHIP_REQUIRED (403), TEAM_LEAD_CONFLICT (409), VISIBILITY_NOT_ALLOWED (400). Team/dự án bị lưu trữ trước duyệt dùng REFERENCE_NO_LONGER_VALID (409).
- Migration bổ sung trường mới cho dữ liệu cũ: team=null; relatedDepartmentIds gồm đơn vị tổ chức; meetingType=GENERAL; visibility=STANDARD chỉ với dữ liệu cũ không có dấu phân loại nhạy cảm. Dữ liệu đã biết nhạy cảm phải là CONFIDENTIAL. Không thể tự suy ra mọi dữ liệu HR từ văn bản, cần danh sách rà soát do người quản lý cung cấp trước khi công khai qua API mới.

### 27.7. Ví dụ áp dụng

| Cuộc họp | Team | Dự án | Đơn vị phối hợp | Loại / riêng tư |
|---|---|---|---|---|
| Team Backend đồng bộ tuần | Backend | Không | IT | SYNC / STANDARD |
| Team QA review dự án A | QA | Dự án A | IT | REVIEW / STANDARD |
| Kế toán và kinh doanh đối soát | Không | Không | Kế toán, kinh doanh | REVIEW / STANDARD |
| HR và kỹ thuật phỏng vấn ứng viên | Không | Không | HR, IT | INTERVIEW / CONFIDENTIAL |
| Quản lý trao đổi lương với nhân viên | Không bắt buộc | Không | Đơn vị chủ trì và đơn vị liên quan | SALARY_REVIEW / CONFIDENTIAL |
| Đào tạo toàn công ty | Không bắt buộc | Không | Các đơn vị được chọn/có người dự | TRAINING / STANDARD |

### 27.8. Tiêu chí nghiệm thu bổ sung

| ID | Tình huống | Kết quả bắt buộc |
|---|---|---|
| AC-67 | Họp team không có dự án | Tạo/duyệt/check-in được, không cần dự án giả |
| AC-68 | Một người ở nhiều team liên phòng ban | Chọn team mình thuộc; không bắt buộc cùng phòng ban và không cấp quyền duyệt cho LEAD |
| AC-69 | Họp nhiều phòng ban, không có team/dự án | Lưu đơn vị chủ trì và tập phối hợp riêng; không cộng trùng thành tổng booking |
| AC-70 | Cùng team và dự án hoặc không có cả hai | Đều hợp lệ khi dữ liệu đáp ứng; meetingType vẫn bắt buộc |
| AC-71 | Phỏng vấn có ứng viên ngoài | Chỉ tính tổng số người; không tạo tài khoản khách hoặc RSVP |
| AC-72 | SALARY_REVIEW/INTERVIEW/PERFORMANCE_REVIEW | Bắt buộc CONFIDENTIAL; từ chối yêu cầu hạ riêng tư |
| AC-73 | Admin vận hành xem/tìm lịch HR không tham dự | Chỉ thấy dữ liệu vận hành, không lọc ra loại/nhóm nhạy cảm bị che |
| AC-74 | Thành viên cùng team/phòng ban nhưng không được mời | Không được xem chi tiết hoặc nhận thông báo cuộc họp tự động |
| AC-75 | Team ARCHIVED/người tổ chức rời team trước duyệt | Không duyệt cho tới khi sửa hợp lệ; lịch đã duyệt/lịch sử không tự thay đổi |
| AC-76 | Đề nghị, notification, history và báo cáo của CONFIDENTIAL | Áp cùng quyền riêng tư; không có đường vòng đọc nội dung bị che |
