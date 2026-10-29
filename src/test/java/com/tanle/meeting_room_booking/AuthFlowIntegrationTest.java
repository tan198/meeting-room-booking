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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test against a real PostgreSQL (Flyway migrations V1..V3 included).
 * Skipped automatically when Docker is not available.
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
class AuthFlowIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@test.local";
    private static final String ADMIN_PASSWORD = "Admin@12345";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    // ---------------------------------------------------------------- tests

    @Test
    void wrongPasswordReturns401() throws Exception {
        JsonNode body = call(jsonPost("/api/v1/auth/login", null,
                Map.of("email", ADMIN_EMAIL, "password", "nope")), 401);

        assertThat(body.get("title").asText()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        call(get("/api/v1/users/me"), 401);
    }

    @Test
    void adminCanManageUsersButRegularUserIsForbidden() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD).get("accessToken").asText();
        String email = uniqueEmail();
        createUser(adminToken, email, "User@12345");

        String userToken = login(email, "User@12345").get("accessToken").asText();

        call(get("/api/v1/users/me").header("Authorization", "Bearer " + userToken), 200);
        call(get("/api/v1/users").header("Authorization", "Bearer " + userToken), 403);
        call(get("/api/v1/users").header("Authorization", "Bearer " + adminToken), 200);
    }

    @Test
    void refreshRotatesTokensAndReuseRevokesTheWholeFamily() throws Exception {
        JsonNode first = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String refresh1 = first.get("refreshToken").asText();

        JsonNode second = refresh(refresh1, 200).get("data");
        String refresh2 = second.get("refreshToken").asText();
        assertThat(refresh2).isNotEqualTo(refresh1);

        refresh(refresh1, 401);
        refresh(refresh2, 401);
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        String refresh = login(ADMIN_EMAIL, ADMIN_PASSWORD).get("refreshToken").asText();

        call(jsonPost("/api/v1/auth/logout", null, Map.of("refreshToken", refresh)), 200);

        refresh(refresh, 401);
    }

    @Test
    void changingPasswordRevokesRefreshTokens() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD).get("accessToken").asText();
        String email = uniqueEmail();
        createUser(adminToken, email, "User@12345");

        JsonNode session = login(email, "User@12345");
        String accessToken = session.get("accessToken").asText();
        String refresh = session.get("refreshToken").asText();

        call(jsonPut("/api/v1/users/me/password", accessToken,
                Map.of("currentPassword", "User@12345", "newPassword", "NewPass@123")), 200);

        refresh(refresh, 401);
        login(email, "NewPass@123");
    }

    @Test
    void deactivatedUserLosesAccessImmediately() throws Exception {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD).get("accessToken").asText();
        String email = uniqueEmail();
        String userId = createUser(adminToken, email, "User@12345");

        JsonNode session = login(email, "User@12345");
        String accessToken = session.get("accessToken").asText();
        String refresh = session.get("refreshToken").asText();

        call(jsonPut("/api/v1/users/" + userId, adminToken,
                Map.of("fullName", "Disabled", "roleName", "USER", "isActive", false)), 200);

        call(get("/api/v1/users/me").header("Authorization", "Bearer " + accessToken), 401);
        refresh(refresh, 401);
        call(jsonPost("/api/v1/auth/login", null, Map.of("email", email, "password", "User@12345")), 403);
    }

    // -------------------------------------------------------------- helpers

    private JsonNode login(String email, String password) throws Exception {
        return call(jsonPost("/api/v1/auth/login", null, Map.of("email", email, "password", password)), 200)
                .get("data");
    }

    private JsonNode refresh(String refreshToken, int expectedStatus) throws Exception {
        return call(jsonPost("/api/v1/auth/refresh", null, Map.of("refreshToken", refreshToken)), expectedStatus);
    }

    private String createUser(String adminToken, String email, String password) throws Exception {
        JsonNode body = call(jsonPost("/api/v1/users", adminToken, Map.of(
                "email", email, "password", password, "fullName", "Test User", "roleName", "USER")), 201);
        return body.get("data").get("id").asText();
    }

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@test.local";
    }

    private MockHttpServletRequestBuilder jsonPost(String url, String token, Object body) throws Exception {
        return withJson(post(url), token, body);
    }

    private MockHttpServletRequestBuilder jsonPut(String url, String token, Object body) throws Exception {
        return withJson(put(url), token, body);
    }

    private MockHttpServletRequestBuilder withJson(MockHttpServletRequestBuilder builder, String token, Object body)
            throws Exception {
        builder.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
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
