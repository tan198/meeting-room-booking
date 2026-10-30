package com.tanle.meeting_room_booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for room allocation, approval, and usage against PostgreSQL 17.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=integration-test-secret-integration-test-1234",
        "app.admin.email=admin@test.local",
        "app.admin.password=Admin@12345",
        "DB_USERNAME=unused",
        "DB_PASSWORD=unused"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RoomAndBookingIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@test.local";
    private static final String ADMIN_PASSWORD = "Admin@12345";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createRoomAndPreventOverlappingBookings() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String organizerToken = createOrganizer(adminToken);
        String roomName = "Room-" + UUID.randomUUID().toString().substring(0, 8);

        JsonNode room = call(jsonPost("/api/v1/rooms", adminToken, Map.of(
                "name", roomName,
                "floor", 3,
                "capacity", 10,
                "facilities", List.of("TV", "Whiteboard")
        )), 201).get("data");
        String roomId = room.get("id").asText();

        OffsetDateTime start = nextDayStart();
        OffsetDateTime end = start.plusHours(1);

        // Yêu cầu mới chỉ PENDING; Admin khác người tổ chức phê duyệt để giữ phòng.
        JsonNode booking1 = call(jsonPost("/api/v1/bookings", organizerToken, Map.of(
                "roomId", roomId,
                "title", "Sprint Planning",
                "startTime", start.toString(),
                "endTime", end.toString(),
                "attendeeCount", 8
        )), 201).get("data");
        assertThat(booking1.get("status").asText()).isEqualTo("PENDING");
        assertThat(booking1.get("roomId").isNull()).isTrue();
        booking1 = approve(adminToken, booking1, roomId, 200).get("data");
        assertThat(booking1.get("status").asText()).isEqualTo("APPROVED");

        // Được gửi yêu cầu trùng giờ, nhưng Admin không được phê duyệt vào phòng đã bận.
        JsonNode overlapping = call(jsonPost("/api/v1/bookings", organizerToken, Map.of(
                "roomId", roomId,
                "title", "Overlapping Meeting",
                "startTime", start.plusMinutes(15).toString(),
                "endTime", end.plusMinutes(15).toString(),
                "attendeeCount", 5
        )), 201).get("data");
        JsonNode conflict = approve(adminToken, overlapping, roomId, 409);
        assertThat(conflict.get("title").asText()).isEqualTo("ROOM_UNAVAILABLE");

        // Lịch nối tiếp [end, end + 1h) được phê duyệt nhờ khoảng nửa đóng '[)'.
        JsonNode backToBack = call(jsonPost("/api/v1/bookings", organizerToken, Map.of(
                "roomId", roomId,
                "title", "Back-to-back Meeting",
                "startTime", end.toString(),
                "endTime", end.plusHours(1).toString(),
                "attendeeCount", 5
        )), 201).get("data");
        approve(adminToken, backToBack, roomId, 200);

        // Hủy booking 1 -> sau đó đặt lại đúng khung giờ cũ phải thành công
        String booking1Id = booking1.get("id").asText();
        JsonNode cancelled = call(jsonPost("/api/v1/bookings/" + booking1Id + "/cancel", organizerToken,
                Map.of("reason", "Đổi kế hoạch", "expectedVersion", booking1.get("version").asLong())), 200).get("data");
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");

        JsonNode rebooked = call(jsonPost("/api/v1/bookings", organizerToken, Map.of(
                "roomId", roomId,
                "title", "Rebooked Slot",
                "startTime", start.toString(),
                "endTime", end.toString(),
                "attendeeCount", 6
        )), 201).get("data");
        approve(adminToken, rebooked, roomId, 200);
    }

    @Test
    void checkInAndCheckOutFlow() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String organizerToken = createOrganizer(adminToken);
        String roomName = "Room-" + UUID.randomUUID().toString().substring(0, 8);

        String roomId = call(jsonPost("/api/v1/rooms", adminToken, Map.of(
                "name", roomName,
                "floor", 2,
                "capacity", 6,
                "facilities", List.of("Projector")
        )), 201).get("data").get("id").asText();

        OffsetDateTime start = nextDayStart();
        OffsetDateTime end = start.plusHours(1);

        JsonNode booking = call(jsonPost("/api/v1/bookings", organizerToken, Map.of(
                "roomId", roomId,
                "title", "Daily Sync",
                "startTime", start.toString(),
                "endTime", end.toString()
        )), 201).get("data");
        approve(adminToken, booking, roomId, 200);
        String bookingId = booking.get("id").asText();

        // Đưa dữ liệu đã duyệt vào cửa sổ check-in hiện tại, không chờ đồng hồ thực.
        start = OffsetDateTime.now().truncatedTo(ChronoUnit.MINUTES).minusMinutes(1);
        end = start.plusHours(1);
        jdbc.update("""
                UPDATE bookings SET start_time = ?, end_time = ?, check_in_deadline = ? WHERE id = ?
                """, start, end, start.plusMinutes(10), UUID.fromString(bookingId));

        JsonNode checkedIn = call(post("/api/v1/bookings/" + bookingId + "/check-in")
                .header("Authorization", "Bearer " + organizerToken), 200).get("data");
        assertThat(checkedIn.get("status").asText()).isEqualTo("APPROVED");
        assertThat(checkedIn.get("usageStatus").asText()).isEqualTo("IN_USE");
        assertThat(checkedIn.get("actualStartTime").isNull()).isFalse();

        // Check-out
        JsonNode checkedOut = call(post("/api/v1/bookings/" + bookingId + "/check-out")
                .header("Authorization", "Bearer " + organizerToken), 200).get("data");
        assertThat(checkedOut.get("usageStatus").asText()).isEqualTo("COMPLETED");
        assertThat(checkedOut.get("actualEndTime").isNull()).isFalse();
        assertThat(OffsetDateTime.parse(checkedOut.get("endTime").asText()).toInstant()).isEqualTo(end.toInstant());
        assertThat(checkedOut.get("releasedAt").asText()).isEqualTo(checkedOut.get("actualEndTime").asText());

        // Check-out sớm giải phóng phần còn lại, dù endTime dự kiến vẫn được giữ nguyên.
        JsonNode available = call(get("/api/v1/rooms/available")
                .param("startTime", start.plusMinutes(5).toString())
                .param("endTime", start.plusMinutes(30).toString())
                .header("Authorization", "Bearer " + adminToken), 200).get("data");
        assertThat(available.isArray()).isTrue();
        assertThat(available.findValuesAsText("id")).contains(roomId);
    }

    private String createOrganizer(String adminToken) throws Exception {
        String email = "organizer-" + UUID.randomUUID() + "@test.local";
        String password = "Organizer@12345";
        call(jsonPost("/api/v1/users", adminToken, Map.of(
                "email", email,
                "password", password,
                "fullName", "Booking Organizer",
                "roleName", "USER"
        )), 201);
        return login(email, password);
    }

    private JsonNode approve(String adminToken, JsonNode booking, String roomId, int expectedStatus) throws Exception {
        return call(jsonPost("/api/v1/bookings/" + booking.get("id").asText() + "/approve", adminToken, Map.of(
                "roomId", roomId,
                "expectedVersion", booking.get("version").asLong()
        )), expectedStatus);
    }

    private OffsetDateTime nextDayStart() {
        return OffsetDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                .plusDays(1).withHour(9).withMinute(0).truncatedTo(ChronoUnit.MINUTES);
    }

    private String login(String email, String password) throws Exception {
        return call(jsonPost("/api/v1/auth/login", null, Map.of("email", email, "password", password)), 200)
                .get("data").get("accessToken").asText();
    }

    private MockHttpServletRequestBuilder jsonPost(String url, String token, Object body) throws Exception {
        MockHttpServletRequestBuilder builder = post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return builder;
    }

    private JsonNode call(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        String json = mvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.isBlank() ? null : mapper.readTree(json);
    }
}

