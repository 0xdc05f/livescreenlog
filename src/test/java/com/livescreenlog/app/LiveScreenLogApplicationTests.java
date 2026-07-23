package com.livescreenlog.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class LiveScreenLogApplicationTests {

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

    @Test
    void contextLoads() {
    }
}
