package au.chipomho.showcase.fleet;

import au.chipomho.showcase.fleet.domain.Fleet;
import au.chipomho.showcase.fleet.simulation.SimulatorRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the application through {@code main}, configured the way compose configures it, against
 * the test containers.
 */
class FleetApplicationTest extends IntegrationTest {

    @Autowired
    private PostgreSQLContainer postgres;

    @Autowired
    private GenericContainer<?> redis;

    @Test
    void mainStartsTheServiceFromItsConfiguration() {
        var started = new AtomicReference<ConfigurableApplicationContext>();
        SpringApplication.withHook(application -> new SpringApplicationRunListener() {
            @Override
            public void started(ConfigurableApplicationContext context, Duration timeTaken) {
                started.set(context);
            }
        }, () -> FleetApplication.main(new String[] {
                "--server.port=0",
                "--fleet.simulator.enabled=false",
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--spring.data.redis.host=" + redis.getHost(),
                "--spring.data.redis.port=" + redis.getMappedPort(6379)
        }));

        assertThat(started).doesNotHaveNullValue();
        try (var context = started.get()) {
            assertThat(context.getBean(Fleet.class).trucks()).hasSize(6);
            assertThat(context.getBean(SimulatorRunner.class).isRunning()).isFalse();
        }
    }
}
