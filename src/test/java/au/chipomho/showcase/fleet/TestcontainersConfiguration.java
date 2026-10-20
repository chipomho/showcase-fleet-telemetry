package au.chipomho.showcase.fleet;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;

/**
 * Real Postgres and Redis, plus a clock and a scheduler the tests control. The containers are
 * beans, so they are stopped and removed when the test context closes. They get longer than the
 * default minute to start, for a busy or shared container engine.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final Duration STARTUP = Duration.ofMinutes(5);

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine")).withStartupTimeout(STARTUP);
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
                .withExposedPorts(6379)
                .withStartupTimeout(STARTUP);
    }

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(Instant.parse("2026-10-20T10:00:00Z"));
    }

    @Bean
    @Primary
    ManualScheduler manualScheduler() {
        return new ManualScheduler();
    }
}
