package com.livescreenlog.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class IngestionIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("livescreenlog")
            .withUsername("livescreenlog_user")
            .withPassword("livescreenlog_password");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("livescreenlog.security.hmac-secret", () -> "test-hmac-secret-key-at-least-32-characters");
        registry.add("livescreenlog.security.project-key", () -> "test-project-key");
        registry.add("livescreenlog.security.dashboard-enabled", () -> "false");
        registry.add("livescreenlog.rate-limit.session-create-per-minute", () -> "1000");
        registry.add("livescreenlog.rate-limit.event-append-per-minute", () -> "1000");
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void createSessionAppendEventsAndRead() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectKey": "test-project-key",
                                  "userId": "user-1",
                                  "distinctId": "d-1",
                                  "source": "/home"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        JsonNode body = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String sessionId = body.get("sessionId").asText();
        String token = body.get("token").asText();

        mockMvc.perform(post("/api/events")
                        .header("x-livescreenlog-session-token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {"type": 2, "timestamp": 1000, "data": {"href": "https://example.com"}},
                                  {"type": 3, "timestamp": 1100, "data": {"source": 1}}
                                ]
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/events")
                        .header("x-livescreenlog-session-token", "invalid.token.value")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/sessions/" + sessionId + "/events?paged=true&limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(2))
                .andExpect(jsonPath("$.hasMore").value(false));

        mockMvc.perform(post("/api/heartbeat")
                        .header("x-livescreenlog-session-token", token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/stop")
                        .header("x-livescreenlog-session-token", token))
                .andExpect(status().isOk());

        MvcResult detail = mockMvc.perform(get("/api/sessions/" + sessionId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode session = objectMapper.readTree(detail.getResponse().getContentAsString());
        assertThat(session.get("status").asText()).isEqualTo("STOPPED");
    }

    @Test
    void appendEventsAcceptsGzipBody() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectKey": "test-project-key",
                                  "userId": "user-gzip",
                                  "source": "/gzip"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(createResult.getResponse().getContentAsString());
        String sessionId = body.get("sessionId").asText();
        String token = body.get("token").asText();

        String json = """
                [
                  {"type": 2, "timestamp": 2000, "data": {"href": "https://gzip.example"}},
                  {"type": 3, "timestamp": 2100, "data": {"source": 2}}
                ]
                """;
        byte[] gzipped = gzip(json.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(post("/api/events")
                        .header("x-livescreenlog-session-token", token)
                        .header("Content-Encoding", "gzip")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gzipped))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/sessions/" + sessionId + "/events?paged=true&limit=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events.length()").value(2));
    }

    private static byte[] gzip(byte[] input) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gos = new GZIPOutputStream(baos)) {
            gos.write(input);
        }
        return baos.toByteArray();
    }

    @Test
    void invalidProjectKeyRejected() throws Exception {
        mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectKey": "unknown-key",
                                  "userId": "u"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pushAdminEndpointsOpenWithoutAuth() throws Exception {
        mockMvc.perform(post("/api/push/trigger-record")
                        .param("projectKey", "test-project-key")
                        .param("userId", "u1"))
                .andExpect(status().isNotFound());
    }
}
