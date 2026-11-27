package com.verifi.portal.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"VERIFI_DEMO_ENABLED=false", "server.servlet.session.cookie.secure=false"})
@Testcontainers
@Import(SessionIntegrationTest.ProbeController.class)
class SessionIntegrationTest {
    @Container
    static final PostgreSQLContainer DATABASE = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", DATABASE::getJdbcUrl);
        properties.add("spring.datasource.username", DATABASE::getUsername);
        properties.add("spring.datasource.password", DATABASE::getPassword);
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    CookieManager cookies;
    HttpClient client;
    UUID organizationId;
    UUID userId;
    String email;
    final String password = "Synthetic-test-password!42";

    @BeforeEach
    void createIsolatedIdentity() {
        cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        client = HttpClient.newBuilder().cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(10)).build();
        organizationId = UUID.randomUUID();
        userId = UUID.randomUUID();
        email = "identity-" + userId + "@example.invalid";
        jdbc.update("insert into organizations(id,name) values (?,?)", organizationId, "Synthetic Test Company");
        jdbc.update("""
                insert into portal_users(id,organization_id,email,display_name,role,password_hash)
                values (?,?,?,?,'CLIENT_ADMIN',?)
                """, userId, organizationId, email, "Synthetic User", passwords.encode(password));
    }

    @Test
    void anonymousSessionProvidesCsrfWithoutCredentials() throws Exception {
        HttpResponse<String> response = get("/api/session");
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = json.readTree(response.body());
        assertThat(body.get("user").isNull()).isTrue();
        assertThat(body.get("csrfToken").asText()).isNotBlank();
        assertThat(response.headers().firstValue("cache-control").orElse("")).contains("no-store");
        assertThat(response.headers().allValues("set-cookie").toString()).contains("HttpOnly");
        assertThat(response.body()).doesNotContain("password", "passwordHash");
    }

    @Test
    void protectedRequestReturnsJson401WithoutRedirect() throws Exception {
        HttpResponse<String> response = get("/api/identity-test/protected");
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(json.readTree(response.body()).get("code").asText()).isEqualTo("UNAUTHENTICATED");
        assertThat(response.headers().firstValue("location")).isEmpty();
    }

    @Test
    void loginRequiresActualSessionCsrfToken() throws Exception {
        HttpResponse<String> missing = post("/api/session/login", null, credentials(email, password));
        assertThat(missing.statusCode()).isEqualTo(403);
        assertThat(json.readTree(missing.body()).get("code").asText()).isEqualTo("CSRF_INVALID");
        HttpResponse<String> invalid = post("/api/session/login", "invalid", credentials(email, password));
        assertThat(invalid.statusCode()).isEqualTo(403);
        assertThat(json.readTree(get("/api/session").body()).get("user").isNull()).isTrue();
    }

    @Test
    void successfulLoginRotatesSessionAndCsrfAndExposesOnlyIdentity() throws Exception {
        String beforeToken = csrf();
        String beforeSession = sessionId();
        HttpResponse<String> login = post("/api/session/login", beforeToken,
                credentials("  " + email.toUpperCase(java.util.Locale.ROOT) + "  ", password));
        assertThat(login.statusCode()).isEqualTo(204);
        assertThat(sessionId()).isNotEqualTo(beforeSession);
        JsonNode session = json.readTree(get("/api/session").body());
        JsonNode user = session.get("user");
        assertThat(user.size()).isEqualTo(5);
        assertThat(user.get("id").asText()).isEqualTo(userId.toString());
        assertThat(user.get("organizationId").asText()).isEqualTo(organizationId.toString());
        assertThat(user.get("email").asText()).isEqualTo(email);
        assertThat(user.get("displayName").asText()).isEqualTo("Synthetic User");
        assertThat(user.get("role").asText()).isEqualTo("CLIENT_ADMIN");
        assertThat(get("/api/identity-test/protected").statusCode()).isEqualTo(200);
        assertThat(post("/api/identity-test/write", beforeToken, "{}").statusCode()).isEqualTo(403);
        assertThat(post("/api/identity-test/write", session.get("csrfToken").asText(), "{}").statusCode())
                .isEqualTo(200);
    }

    @Test
    void invalidAndUnknownCredentialsReturnTheSameGenericFailure() throws Exception {
        String token = csrf();
        HttpResponse<String> wrong = post("/api/session/login", token, credentials(email, "wrong-password"));
        HttpResponse<String> unknown = post("/api/session/login", token,
                credentials("missing-" + UUID.randomUUID() + "@example.invalid", password));
        assertThat(wrong.statusCode()).isEqualTo(401);
        assertThat(unknown.statusCode()).isEqualTo(401);
        assertThat(wrong.body()).isEqualTo(unknown.body()).doesNotContain(email, password);
        assertThat(json.readTree(wrong.body()).get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(json.readTree(get("/api/session").body()).get("user").isNull()).isTrue();
    }

    @Test
    void disabledAccountCannotLogIn() throws Exception {
        jdbc.update("update portal_users set enabled=false where id=?", userId);
        assertThat(post("/api/session/login", csrf(), credentials(email, password)).statusCode()).isEqualTo(401);
    }

    @Test
    void malformedOrOversizedCredentialsCannotCauseServerError() throws Exception {
        String token = csrf();
        assertThat(post("/api/session/login", token, "{\"email\":null,\"password\":null}").statusCode())
                .isEqualTo(401);
        assertThat(post("/api/session/login", token, credentials(email, "x".repeat(73))).statusCode())
                .isEqualTo(401);
        assertThat(post("/api/session/login", token, "{").statusCode()).isEqualTo(400);
    }

    @Test
    void memberCanUseProtectedApiButCannotUseAdminAction() throws Exception {
        jdbc.update("update portal_users set role='MEMBER' where id=?", userId);
        assertThat(post("/api/session/login", csrf(), credentials(email, password)).statusCode()).isEqualTo(204);
        assertThat(get("/api/identity-test/protected").statusCode()).isEqualTo(200);
        HttpResponse<String> denied = get("/api/identity-test/admin");
        assertThat(denied.statusCode()).isEqualTo(403);
        assertThat(json.readTree(denied.body()).get("code").asText()).isEqualTo("FORBIDDEN");
    }

    @Test
    void logoutRequiresCsrfAndInvalidatesAuthentication() throws Exception {
        assertThat(post("/api/session/login", csrf(), credentials(email, password)).statusCode()).isEqualTo(204);
        String token = csrf();
        assertThat(post("/api/session/logout", null, "{}").statusCode()).isEqualTo(403);
        assertThat(get("/api/identity-test/protected").statusCode()).isEqualTo(200);
        assertThat(post("/api/session/logout", token, "{}").statusCode()).isEqualTo(204);
        assertThat(get("/api/identity-test/protected").statusCode()).isEqualTo(401);
        JsonNode after = json.readTree(get("/api/session").body());
        assertThat(after.get("user").isNull()).isTrue();
        assertThat(after.get("csrfToken").asText()).isNotBlank();
        assertThat(post("/api/session/login", token, credentials(email, password)).statusCode()).isEqualTo(403);
        assertThat(post("/api/session/login", after.get("csrfToken").asText(), credentials(email, password)).statusCode())
                .isEqualTo(204);
    }

    private String csrf() throws Exception {
        return json.readTree(get("/api/session").body()).get("csrfToken").asText();
    }

    private String sessionId() {
        return cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> cookie.getName().equals("JSESSIONID"))
                .findFirst().orElseThrow().getValue();
    }

    private String credentials(String email, String password) {
        return json.writeValueAsString(Map.of("email", email, "password", password));
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String csrf, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json");
        if (csrf != null) request.header("X-CSRF-TOKEN", csrf);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @TestComponent
    @RestController
    static class ProbeController {
        @GetMapping("/api/identity-test/protected")
        Map<String, Object> protectedRead(@AuthenticationPrincipal CurrentActor actor) {
            return Map.of("organizationId", actor.organizationId(), "role", actor.role());
        }

        @PostMapping("/api/identity-test/write")
        Map<String, Boolean> protectedWrite() { return Map.of("accepted", true); }

        @GetMapping("/api/identity-test/admin")
        @PreAuthorize("hasRole('CLIENT_ADMIN')")
        Map<String, Boolean> adminRead() { return Map.of("accepted", true); }
    }
}
