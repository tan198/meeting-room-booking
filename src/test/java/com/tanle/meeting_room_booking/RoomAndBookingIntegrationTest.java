package com.tanle.meeting_room_booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests for Room & Booking modules against PostgreSQL 17 (including Exclusion Constraint & State Machine).
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

    @Test
    void createRoomAndPreventOverlappingBookings() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String roomName = "Room-" + UUID.randomUUID().toString().substring(0, 8);

        JsonNode room = call(jsonPost("/api/v1/rooms", adminToken, Map.of(
                "name", roomName,
                "floor", 3,
                "capacity", 10,
                "facilities", List.of("TV", "Whiteboard")
        )), 201).get("data");
        String roomId = room.get("id").asText();

        OffsetDateTime start = OffsetDateTime.now().plusHours(2);
        OffsetDateTime end = start.plusHours(1);

        // Đặt phòng lần 1 -> 201 CREATED
        JsonNode booking1 = call(jsonPost("/api/v1/bookings", adminToken, Map.of(
                "roomId", roomId,
                "title", "Sprint Planning",
                "startTime", start.toString(),
                "endTime", end.toString(),
                "attendeeCount", 8
        )), 201).get("data");
        assertThat(booking1.get("status").asText()).isEqualTo("CONFIRMED");

        // Đặt trùng khung giờ -> 409 CONFLICT (ROOM_TIME_CONFLICT)
        JsonNode conflict = call(jsonPost("/api/v1/bookings", adminToken, Map.of(
                "roomId", roomId,
                "title", "Overlapping Meeting",
                "startTime", start.plusMinutes(15).toString(),
                "endTime", end.plusMinutes(15).toString(),
                "attendeeCount", 5
        )), 409);
        assertThat(conflict.get("title").asText()).isEqualTo("ROOM_TIME_CONFLICT");

        // Đặt nối tiếp [end, end + 1h) -> 201 CREATED (nhờ khoảng nửa đóng '[)')
        call(jsonPost("/api/v1/bookings", adminToken, Map.of(
                "roomId", roomId,
                "title", "Back-to-back Meeting",
                "startTime", end.toString(),
                "endTime", end.plusHours(1).toString(),
                "attendeeCount", 5
        )), 201);

        // Hủy booking 1 -> sau đó đặt lại đúng khung giờ cũ phải thành công
        String booking1Id = booking1.get("id").asText();
        JsonNode cancelled = call(jsonPost("/api/v1/bookings/" + booking1Id + "/cancel", adminToken,
                Map.of("reason", "Đổi kế hoạch")), 200).get("data");
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");

        call(jsonPost("/api/v1/bookings", adminToken, Map.of(
                "roomId", roomId,
                "title", "Rebooked Slot",
                "startTime", start.toString(),
                "endTime", end.toString(),
                "attendeeCount", 6
        )), 201);
    }

    @Test
    void checkInAndCheckOutFlow() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String roomName = "Room-" + UUID.randomUUID().toString().substring(0, 8);

        String roomId = call(jsonPost("/api/v1/rooms", adminToken, Map.of(
                "name", roomName,
                "floor", 2,
                "capacity", 6,
                "facilities", List.of("Projector")
        )), 201).get("data").get("id").asText();

        OffsetDateTime start = OffsetDateTime.now().plusMinutes(5);
        OffsetDateTime end = start.plusHours(1);

        String bookingId = call(jsonPost("/api/v1/bookings", adminToken, Map.of(
                "roomId", roomId,
                "title", "Daily Sync",
                "startTime", start.toString(),
                "endTime", end.toString()
        )), 201).get("data").get("id").asText();

        // Check-in (nằm trong cửa sổ sớm 15 phút)
        JsonNode checkedIn = call(post("/api/v1/bookings/" + bookingId + "/check-in")
                .header("Authorization", "Bearer " + adminToken), 200).get("data");
        assertThat(checkedIn.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(checkedIn.get("actualStartTime").isNull()).isFalse();

        // Check-out
        JsonNode checkedOut = call(post("/api/v1/bookings/" + bookingId + "/check-out")
                .header("Authorization", "Bearer " + adminToken), 200).get("data");
        assertThat(checkedOut.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(checkedOut.get("actualEndTime").isNull()).isFalse();

        // Kiểm tra endpoint tìm phòng trống
        JsonNode available = call(get("/api/v1/rooms/available")
                .param("startTime", start.plusHours(3).toString())
                .param("endTime", start.plusHours(4).toString())
                .header("Authorization", "Bearer " + adminToken), 200).get("data");
        assertThat(available.isArray()).isTrue();
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

