package au.chipomho.showcase.fleet;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * The whole service against real Postgres and Redis. Every subclass shares one application
 * context, and so one pair of containers. Before each test Redis and the alert table are emptied
 * and the clock moves on a minute, past every alert window. Which alerts are active is remembered
 * in memory, though, so each test that expects an alert uses a truck and alert type no other test
 * does. The simulator is on, but only ticks when a test steps the scheduler.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "fleet.simulator.trucks=8",
        "fleet.simulator.seed=11",
        "fleet.allowed-origins=https://dashboard.example, https://other-dashboard.example"
})
@Import(TestcontainersConfiguration.class)
@AutoConfigureRestTestClient
public abstract class IntegrationTest {

    protected static final String ALLOWED_ORIGIN = "https://dashboard.example";

    @Autowired
    protected RestTestClient client;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected ManualScheduler scheduler;

    @LocalServerPort
    protected int port;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void startFromNothing() {
        clock.advance(Duration.ofMinutes(1));
        redis.delete(redis.keys("truck:*"));
        jdbc.sql("delete from alert").update();
    }

    protected void post(String readingJson) {
        client.post().uri("/v1/telemetry")
                .contentType(MediaType.APPLICATION_JSON)
                .body(readingJson)
                .exchange()
                .expectStatus().isAccepted();
    }

    /**
     * A reading's JSON, on the haul road between the shovel and the crusher.
     */
    protected static String reading(String truckId, Instant at, double speedKph, double engineTempC, double fuelPct,
                                    double payloadTonnes) {
        return String.format(Locale.ROOT, "{\"truckId\":\"%s\",\"recordedAt\":\"%s\",\"position\":{\"x\":2000.0,"
                        + "\"y\":1500.0},\"speedKph\":%s,\"engineTempC\":%s,\"fuelPct\":%s,\"payloadTonnes\":%s}",
                truckId, at, speedKph, engineTempC, fuelPct, payloadTonnes);
    }
}
